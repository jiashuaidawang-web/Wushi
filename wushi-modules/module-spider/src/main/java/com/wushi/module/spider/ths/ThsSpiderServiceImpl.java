package com.wushi.module.spider.ths;

import com.wushi.module.market.domain.row.StockPlateDailyKlineRow;
import com.wushi.module.market.domain.row.StockPlateDimensionRow;
import com.wushi.module.market.domain.row.StockPlateRelationSnapshotRow;
import com.wushi.module.spider.common.SpiderHttpClient;
import com.wushi.module.spider.eastmoney.KuaidailiProxyRefresher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.openqa.selenium.*;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.interactions.Actions;
import org.openqa.selenium.remote.RemoteWebDriver;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.time.Duration;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 同花顺爬虫服务实现
 * 使用 Selenium 4.x + 远端 Docker (Selenium Grid/Standalone) + 代理池
 *
 * 核心策略:
 * 1. 板块维度: 从同花顺板块页面抓取板块列表和行情
 * 2. 个股关系: 从板块详情页抓取板块下个股
 * 3. 反爬应对: 代理池轮换 + 浏览器指纹模拟 + 滑块检测 + 快代理IP自动刷新
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ThsSpiderServiceImpl {

    private static final String THS_HOME = "http://q.10jqka.com.cn/";
    private static final By PLATE_LINKS_SELECTOR = By.cssSelector("a[href*='/code/']");
    private static final int MAX_BLOCK_RETRIES = 3;

    private final SpiderHttpClient httpClient;
    private final ThsBrowserProperties browserProperties;
    private final ThsProxyProvider proxyProvider;
    private final KuaidailiProxyRefresher kuaidailiRefresher;
    private final ThreadLocal<WebDriver> currentDriver = new ThreadLocal<>();
    private final AtomicBoolean running = new AtomicBoolean(false);

    /**
     * 同步当日所有同花顺数据
     * @return 抓取结果统计
     */
    public Map<String, Object> syncDaily(LocalDate tradeDate) {
        if (!running.compareAndSet(false, true)) {
            throw new IllegalStateException("同花顺跑批任务正在执行中");
        }
        try {
            // 1. 抓取板块维度 + 板块行情
            List<StockPlateDimensionRow> dimensions = fetchAllPlates(tradeDate);
            // 2. 抓取板块个股关系
            List<StockPlateRelationSnapshotRow> relations = fetchAllPlateRelations(tradeDate);

            Map<String, Object> result = new HashMap<>();
            result.put("tradeDate", tradeDate.toString());
            result.put("source", "ths");
            result.put("plateCount", dimensions.size());
            result.put("relationCount", relations.size());
            return result;
        } finally {
            running.set(false);
            cleanupDriver();
        }
    }

    /**
     * 抓取所有板块(概念/地域/行业)
     */
    public List<StockPlateDimensionRow> fetchAllPlates(LocalDate tradeDate) {
        List<StockPlateDimensionRow> allPlates = new ArrayList<>();
        String[][] categories = {
                {"gn", "CONCEPT", "概念"},
                {"dy", "REGION", "地域"},
                {"thshy", "INDUSTRY", "行业"},
                {"zjhhy", "CSRC_INDUSTRY", "证监会行业"}
        };

        for (String[] cat : categories) {
            try {
                String url = THS_HOME + cat[0] + "/";
                List<StockPlateDimensionRow> plates = fetchPlatesFromPageWithRetry(url, cat[1], tradeDate);
                allPlates.addAll(plates);
                log.info("同花顺{}板块抓取完成: count={}", cat[2], plates.size());
            } catch (Exception e) {
                log.warn("同花顺{}板块抓取失败: {}", cat[2], e.getMessage());
            }
            sleep(1000);
        }
        return allPlates;
    }

    /**
     * 从板块列表页抓取板块 (带IP被封自动换代理重试)
     */
    private List<StockPlateDimensionRow> fetchPlatesFromPageWithRetry(String url, String plateType, LocalDate tradeDate) {
        for (int attempt = 0; attempt < MAX_BLOCK_RETRIES; attempt++) {
            WebDriver driver = null;
            try {
                driver = createDriver();
                currentDriver.set(driver);
                driver.get(url);
                sleep(2000);

                if (detectCaptcha(driver)) {
                    log.warn("同花顺页面检测到验证码/滑块, attempt={}, url={}", attempt + 1, url);
                    if (kuaidailiRefresher.isEnabled() && attempt < MAX_BLOCK_RETRIES - 1) {
                        String newIp = kuaidailiRefresher.refreshOnBlock();
                        if (newIp != null) {
                            log.info("快代理已换新IP重试: {}", newIp);
                            cleanupDriver();
                            continue;
                        }
                    }
                    handleCaptcha(driver);
                }

                List<StockPlateDimensionRow> plates = new ArrayList<>();
                List<WebElement> links = driver.findElements(By.cssSelector("a[href*='/code/']"));
                for (WebElement link : links) {
                    try {
                        String href = link.getAttribute("href");
                        String text = link.getText();
                        if (href == null || text == null) continue;

                        Matcher matcher = Pattern.compile("/code/(\\d{6})").matcher(href);
                        if (matcher.find()) {
                            String plateCode = matcher.group(1);
                            plates.add(new StockPlateDimensionRow(
                                    plateCode,
                                    text.trim(),
                                    plateType,
                                    "",
                                    "ACTIVE",
                                    "0"
                            ));
                        }
                    } catch (StaleElementReferenceException ignored) {
                    }
                }
                return plates;
            } catch (Exception e) {
                log.error("同花顺板块页面抓取失败: url={}, attempt={}, error={}", url, attempt + 1, e.getMessage());
                if (kuaidailiRefresher.isEnabled() && attempt < MAX_BLOCK_RETRIES - 1) {
                    kuaidailiRefresher.refreshOnBlock();
                }
            } finally {
                cleanupDriver();
            }
        }
        return List.of();
    }

    /**
     * 抓取所有板块下的个股关系
     */
    public List<StockPlateRelationSnapshotRow> fetchAllPlateRelations(LocalDate tradeDate) {
        List<StockPlateRelationSnapshotRow> allRelations = new ArrayList<>();
        WebDriver driver = null;

        try {
            driver = createDriver();
            currentDriver.set(driver);

            // 只处理概念板块(数量最多)
            driver.get(THS_HOME + "gn/");
            sleep(2000);

            if (detectCaptcha(driver)) {
                if (kuaidailiRefresher.isEnabled()) {
                    String newIp = kuaidailiRefresher.refreshOnBlock();
                    if (newIp != null) {
                        log.info("快代理换新IP后重试关系页: {}", newIp);
                        cleanupDriver();
                        driver = createDriver();
                        currentDriver.set(driver);
                        driver.get(THS_HOME + "gn/");
                        sleep(2000);
                    }
                } else {
                    handleCaptcha(driver);
                }
            }

            // 获取所有概念板块链接
            List<WebElement> plateLinks = driver.findElements(By.cssSelector("a[href*='/code/']"));
            Set<String> visitedPlates = new HashSet<>();

            for (WebElement link : plateLinks) {
                try {
                    String href = link.getAttribute("href");
                    String plateName = link.getText();
                    if (href == null) continue;

                    Matcher matcher = Pattern.compile("/code/(\\d{6})").matcher(href);
                    if (!matcher.find()) continue;
                    String plateCode = matcher.group(1);
                    if (visitedPlates.contains(plateCode)) continue;
                    visitedPlates.add(plateCode);

                    // 访问板块详情页抓取个股
                    List<StockPlateRelationSnapshotRow> relations =
                            fetchPlateStocks(driver, plateCode, plateName, "CONCEPT", tradeDate);
                    allRelations.addAll(relations);

                    sleep(800); // 礼貌延迟
                } catch (StaleElementReferenceException ignored) {
                } catch (Exception e) {
                    log.warn("抓取板块个股失败: {}", e.getMessage());
                }
            }

            log.info("同花顺个股关系抓取完成: count={}", allRelations.size());
            return allRelations;
        } catch (Exception e) {
            log.error("同花顺个股关系抓取失败: {}", e.getMessage());
            return allRelations;
        } finally {
            cleanupDriver();
        }
    }

    /**
     * 抓取板块下的个股(复用driver)
     */
    private List<StockPlateRelationSnapshotRow> fetchPlateStocks(WebDriver driver, String plateCode, String plateName,
                                                                 String plateType, LocalDate tradeDate) {
        try {
            driver.get(THS_HOME + "code/" + plateCode + "/");
            sleep(1500);

            if (detectCaptcha(driver)) {
                log.warn("板块个股页检测到验证码, plateCode={}", plateCode);
                handleCaptcha(driver);
            }

            List<StockPlateRelationSnapshotRow> relations = new ArrayList<>();
            List<WebElement> links = driver.findElements(By.cssSelector("table.m_table tbody tr"));
            for (WebElement link : links) {
                try {
                    String text = link.getText();
                    if (text == null) continue;
                    // 提取6位数字代码
                    Matcher m = Pattern.compile("\\b(\\d{6})\\b").matcher(text);
                    if (m.find()) {
                        String stockCode = m.group(1);
                        relations.add(new StockPlateRelationSnapshotRow(
                                tradeDate,
                                stockCode,
                                "",
                                plateCode,
                                plateName,
                                plateType,
                                "HISTORICAL_CRAWLED",
                                new BigDecimal("0.7000"),
                                1,
                                "0"
                        ));
                    }
                } catch (StaleElementReferenceException ignored) {
                }
            }
            return relations;
        } catch (Exception e) {
            log.warn("抓取板块个股失败: plateCode={}, error={}", plateCode, e.getMessage());
            return List.of();
        }
    }

    /**
     * 创建 WebDriver
     * 优先使用远端 Docker (Selenium Grid/Standalone), 否则使用本地 Chrome
     */
    private WebDriver createDriver() {
        ChromeOptions options = new ChromeOptions();
        if (browserProperties.isHeadless()) {
            options.addArguments("--headless=new");
        }
        // 反检测
        options.addArguments(browserProperties.getArguments());
        options.setExperimentalOption("excludeSwitches", List.of("enable-automation"));
        options.setExperimentalOption("useAutomationExtension", false);

        // 代理 - 优先快代理
        String proxy = selectProxy();
        if (proxy != null && !proxy.isBlank()) {
            options.addArguments("--proxy-server=http://" + proxy);
            log.debug("使用代理: {}", proxyLabel(proxy));
        }

        WebDriver driver;
        if (StringUtils.hasText(browserProperties.getRemoteUrl())) {
            // 远端 Docker (Selenium Grid / Standalone)
            URL remoteUrl = toRemoteUrl(browserProperties.getRemoteUrl());
            driver = new RemoteWebDriver(remoteUrl, options);
            log.info("使用远端 Selenium: {}", browserProperties.getRemoteUrl());
        } else if (StringUtils.hasText(browserProperties.getChromeBinary())) {
            options.setBinary(browserProperties.getChromeBinary());
            driver = new ChromeDriver(options);
        } else {
            driver = new ChromeDriver(options);
        }

        driver.manage().timeouts().pageLoadTimeout(Duration.ofSeconds(browserProperties.getPageLoadTimeoutSeconds()));
        driver.manage().window().maximize();
        return driver;
    }

    /**
     * 检测是否有验证码/滑块
     */
    private boolean detectCaptcha(WebDriver driver) {
        try {
            String pageSource = driver.getPageSource();
            if (pageSource == null) return false;
            String lower = pageSource.toLowerCase();
            return lower.contains("captcha") || lower.contains("滑块") || lower.contains("验证")
                    || lower.contains("verify") || lower.contains("访问过于频繁");
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 处理验证码/滑块 (人工介入或重试)
     */
    private void handleCaptcha(WebDriver driver) {
        log.warn("检测到反爬验证, 尝试刷新...");
        sleep(3000);
        driver.navigate().refresh();
        sleep(2000);
    }

    /**
     * 选择代理 - 优先快代理
     */
    private String selectProxy() {
        if (kuaidailiRefresher.isEnabled()) {
            String proxy = kuaidailiRefresher.nextProxy();
            if (proxy != null) return proxy;
        }
        List<String> proxies = proxyProvider.availableProxies();
        if (proxies.isEmpty()) {
            return null;
        }
        // 随机选一个
        return proxies.get(new Random().nextInt(proxies.size()));
    }

    private String proxyLabel(String proxy) {
        return proxy == null || proxy.isBlank() ? "DIRECT" : proxy.substring(0, Math.min(proxy.length(), 20)) + "...";
    }

    private URL toRemoteUrl(String remoteUrl) {
        try {
            return URI.create(remoteUrl).toURL();
        } catch (MalformedURLException | IllegalArgumentException e) {
            throw new IllegalStateException("非法的远端 Selenium 地址: " + remoteUrl, e);
        }
    }

    private void cleanupDriver() {
        WebDriver driver = currentDriver.get();
        if (driver != null) {
            try {
                driver.quit();
            } catch (Exception ignored) {
            }
            currentDriver.remove();
        }
    }

    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
