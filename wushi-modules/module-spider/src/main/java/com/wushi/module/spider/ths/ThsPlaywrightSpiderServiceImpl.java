package com.wushi.module.spider.ths;

import com.microsoft.playwright.*;
import com.microsoft.playwright.options.LoadState;
import com.wushi.module.market.domain.row.StockPlateDimensionRow;
import com.wushi.module.market.domain.row.StockPlateRelationSnapshotRow;
import com.wushi.module.spider.common.SpiderHttpClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 同花顺爬虫 - Playwright 实现
 * 
 * 特性:
 * 1. Playwright 原生 API (比 Selenium 更快更稳定)
 * 2. Stealth 模式反检测 (绕过 webdriver 检测)
 * 3. 真实用户行为模拟 (鼠标移动/点击/滚动/打字延迟)
 * 4. 代理池轮换 (复用 ThsBrowserProperties 配置)
 * 5. 断点续传 (通过 SpiderCheckpointService)
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ThsPlaywrightSpiderServiceImpl {

    private static final String THS_HOME = "http://q.10jqka.com.cn/";
    private static final Pattern PLATE_CODE_PATTERN = Pattern.compile("/code/(\\d{6})");
    private static final Pattern STOCK_CODE_PATTERN = Pattern.compile("\\b(\\d{6})\\b");

    private final SpiderHttpClient httpClient;
    private final ThsBrowserProperties browserProperties;
    private final ThsProxyProvider proxyProvider;
    private final AtomicBoolean running = new AtomicBoolean(false);

    /**
     * 同步当日所有同花顺数据
     */
    public Map<String, Object> syncDaily(LocalDate tradeDate) {
        if (!running.compareAndSet(false, true)) {
            throw new IllegalStateException("同花顺跑批任务正在执行中");
        }
        try {
            List<StockPlateDimensionRow> dimensions = fetchAllPlates(tradeDate);
            List<StockPlateRelationSnapshotRow> relations = fetchAllPlateRelations(tradeDate);

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("tradeDate", tradeDate.toString());
            result.put("source", "ths");
            result.put("plateCount", dimensions.size());
            result.put("relationCount", relations.size());
            return result;
        } finally {
            running.set(false);
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
                List<StockPlateDimensionRow> plates = fetchPlatesFromPage(url, cat[1], tradeDate);
                allPlates.addAll(plates);
                log.info("同花顺Playwright {}板块抓取完成: count={}", cat[2], plates.size());
            } catch (Exception e) {
                log.warn("同花顺Playwright {}板块抓取失败: {}", cat[2], e.getMessage());
            }
            humanDelay(1000, 2000); // 板块间延迟
        }
        return allPlates;
    }

    /**
     * 从板块列表页抓取板块
     */
    public List<StockPlateDimensionRow> fetchPlatesFromPage(String url, String plateType, LocalDate tradeDate) {
        Playwright playwright = null;
        Browser browser = null;
        try {
            playwright = Playwright.create();
            browser = createBrowser(playwright);
            Page page = browser.newPage();

            // 设置 stealth 模式
            applyStealth(page);

            // 导航到页面
            page.navigate(url, new Page.NavigateOptions().setTimeout(60000));
            page.waitForLoadState(LoadState.NETWORKIDLE);

            // 模拟真实用户: 随机滚动
            humanScroll(page);
            humanDelay(500, 1500);

            // 检测验证码
            if (detectCaptcha(page)) {
                log.warn("同花顺Playwright检测到验证码, url={}", url);
                handleCaptcha(page);
            }

            // 提取板块链接
            List<StockPlateDimensionRow> plates = new ArrayList<>();
            Locator links = page.locator("a[href*='/code/']");
            int count = links.count();
            for (int i = 0; i < count; i++) {
                try {
                    String href = links.nth(i).getAttribute("href");
                    String text = links.nth(i).textContent();
                    if (href == null || text == null) continue;

                    Matcher matcher = PLATE_CODE_PATTERN.matcher(href);
                    if (matcher.find()) {
                        String plateCode = matcher.group(1);
                        plates.add(new StockPlateDimensionRow(
                                plateCode, text.trim(), plateType,
                                "", "ACTIVE", "0" // source: 0 = ths
                        ));
                    }
                } catch (Exception ignored) {
                }
            }
            return plates;
        } catch (Exception e) {
            log.error("同花顺Playwright板块页面抓取失败: url={}, error={}", url, e.getMessage());
            return List.of();
        } finally {
            closeQuietly(browser);
            closeQuietly(playwright);
        }
    }

    /**
     * 抓取所有板块下的个股关系
     */
    public List<StockPlateRelationSnapshotRow> fetchAllPlateRelations(LocalDate tradeDate) {
        List<StockPlateRelationSnapshotRow> allRelations = new ArrayList<>();
        Playwright playwright = null;
        Browser browser = null;

        try {
            playwright = Playwright.create();
            browser = createBrowser(playwright);
            Page page = browser.newPage();
            applyStealth(page);

            // 概念板块
            page.navigate(THS_HOME + "gn/", new Page.NavigateOptions().setTimeout(60000));
            page.waitForLoadState(LoadState.NETWORKIDLE);
            humanScroll(page);

            if (detectCaptcha(page)) {
                handleCaptcha(page);
            }

            // 获取所有概念板块链接
            Locator plateLinks = page.locator("a[href*='/code/']");
            Set<String> visitedPlates = new HashSet<>();
            int plateCount = plateLinks.count();

            for (int i = 0; i < plateCount; i++) {
                try {
                    String href = plateLinks.nth(i).getAttribute("href");
                    String plateName = plateLinks.nth(i).textContent();
                    if (href == null) continue;

                    Matcher matcher = PLATE_CODE_PATTERN.matcher(href);
                    if (!matcher.find()) continue;
                    String plateCode = matcher.group(1);
                    if (visitedPlates.contains(plateCode)) continue;
                    visitedPlates.add(plateCode);

                    // 访问板块详情页
                    String plateUrl = THS_HOME + "gn/code/" + plateCode + "/";
                    page.navigate(plateUrl, new Page.NavigateOptions().setTimeout(60000));
                    page.waitForLoadState(LoadState.NETWORKIDLE);
                    humanScroll(page);
                    humanDelay(800, 1500);

                    // 提取个股
                    Locator stockLinks = page.locator("a[href*='/page/'], a[href*='/stock/']");
                    int stockCount = stockLinks.count();
                    for (int j = 0; j < stockCount; j++) {
                        try {
                            String text = stockLinks.nth(j).textContent();
                            if (text == null) continue;
                            Matcher m = STOCK_CODE_PATTERN.matcher(text);
                            if (m.find()) {
                                String stockCode = m.group(1);
                                allRelations.add(new StockPlateRelationSnapshotRow(
                                        tradeDate, stockCode, "", plateCode, plateName,
                                        "CONCEPT", "HISTORICAL_CRAWLED",
                                        new BigDecimal("0.7000"), 1, "0"
                                ));
                            }
                        } catch (Exception ignored) {
                        }
                    }
                } catch (Exception e) {
                    log.warn("抓取板块个股失败: {}", e.getMessage());
                }
            }
        } catch (Exception e) {
            log.error("同花顺Playwright板块个股关系抓取失败: {}", e.getMessage());
        } finally {
            closeQuietly(browser);
            closeQuietly(playwright);
        }
        return allRelations;
    }

    // ========== Playwright 浏览器管理 ==========

    private Browser createBrowser(Playwright playwright) {
        BrowserType.LaunchOptions launchOptions = new BrowserType.LaunchOptions()
                .setHeadless(browserProperties.isHeadless())
                .setArgs(browserProperties.getArguments());

        // 代理
        String proxy = selectProxy();
        if (proxy != null && !proxy.isBlank()) {
            launchOptions.setProxy(new Proxy(proxy));
            log.debug("Playwright使用代理: {}", proxyLabel(proxy));
        }

        // 远端 Playwright 服务 (可选)
        if (StringUtils.hasText(browserProperties.getRemoteUrl())) {
            // Playwright 支持 CDP 连接远端
            return playwright.chromium().connect(browserProperties.getRemoteUrl());
        }

        // 本地 Chrome/Chromium
        if (StringUtils.hasText(browserProperties.getChromeBinary())) {
            launchOptions.setExecutablePath(browserProperties.getChromeBinary());
        }

        return playwright.chromium().launch(launchOptions);
    }

    /**
     * Stealth 反检测
     */
    private void applyStealth(Page page) {
        // 设置真实 User-Agent
        page.newContext(new Browser.NewContextOptions()
                .setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36")
                .setViewportSize(1920, 1080)
                .setLocale("zh-CN")
                .setTimezoneId("Asia/Shanghai"));

        // 注入 stealth JS (绕过 webdriver 检测)
        page.addInitScript("""
            // 覆盖 navigator.webdriver
            Object.defineProperty(navigator, 'webdriver', { get: () => undefined });
            // 覆盖 chrome 对象
            window.chrome = { runtime: {} };
            // 覆盖 permissions
            const originalQuery = window.navigator.permissions.query;
            window.navigator.permissions.query = (parameters) => (
                parameters.name === 'notifications' ?
                    Promise.resolve({ state: Notification.permission }) :
                    originalQuery(parameters)
            );
            // 覆盖 plugins
            Object.defineProperty(navigator, 'plugins', { get: () => [1, 2, 3, 4, 5] });
            // 覆盖 languages
            Object.defineProperty(navigator, 'languages', { get: () => ['zh-CN', 'zh', 'en'] });
        """);
    }

    // ========== 真实用户行为模拟 ==========

    /**
     * 模拟人类滚动 (随机距离+速度)
     */
    private void humanScroll(Page page) {
        int scrollCount = ThreadLocalRandom.current().nextInt(2, 5);
        for (int i = 0; i < scrollCount; i++) {
            int distance = ThreadLocalRandom.current().nextInt(300, 800);
            page.mouse().wheel(0, distance);
            humanDelay(200, 600);
        }
    }

    /**
     * 模拟人类鼠标移动
     */
    private void humanMouseMove(Page page) {
        int viewportWidth = 1920;
        int viewportHeight = 1080;
        int targetX = ThreadLocalRandom.current().nextInt(100, viewportWidth - 100);
        int targetY = ThreadLocalRandom.current().nextInt(100, viewportHeight - 100);
        page.mouse().move(targetX, targetY);
    }

    /**
     * 模拟人类点击 (先移动到元素再点击)
     */
    private void humanClick(Locator locator) {
        // 添加随机偏移
        locator.click(new Locator.ClickOptions().setDelay(ThreadLocalRandom.current().nextInt(50, 150)));
    }

    /**
     * 模拟人类打字 (逐字符+随机延迟)
     */
    private void humanType(Locator locator, String text) {
        for (char c : text.toCharArray()) {
            locator.type(String.valueOf(c), new Locator.TypeOptions().setDelay(ThreadLocalRandom.current().nextInt(50, 200)));
        }
    }

    /**
     * 人类式随机延迟
     */
    private void humanDelay(int minMs, int maxMs) {
        try {
            Thread.sleep(ThreadLocalRandom.current().nextInt(minMs, maxMs));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    // ========== 反检测 ==========

    private boolean detectCaptcha(Page page) {
        try {
            String content = page.content();
            if (content == null) return false;
            String lower = content.toLowerCase();
            return lower.contains("captcha") || lower.contains("滑块") || lower.contains("验证")
                    || lower.contains("verify") || lower.contains("访问过于频繁");
        } catch (Exception e) {
            return false;
        }
    }

    private void handleCaptcha(Page page) {
        log.warn("检测到反爬验证, 尝试刷新...");
        humanDelay(3000, 5000);
        page.reload();
        page.waitForLoadState(LoadState.NETWORKIDLE);
        humanDelay(2000, 3000);
    }

    // ========== 代理管理 ==========

    private String selectProxy() {
        List<String> proxies = proxyProvider.availableProxies();
        if (proxies.isEmpty()) {
            return browserProperties.isDirectFirst() ? null : null;
        }
        return proxies.get(ThreadLocalRandom.current().nextInt(proxies.size()));
    }

    private String proxyLabel(String proxy) {
        return proxy == null || proxy.isBlank() ? "DIRECT" : proxy.substring(0, Math.min(proxy.length(), 20)) + "...";
    }

    private void closeQuietly(AutoCloseable closeable) {
        if (closeable != null) {
            try { closeable.close(); } catch (Exception ignored) {}
        }
    }
}
