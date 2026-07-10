package com.wushi.module.spider.ths;

import com.microsoft.playwright.*;
import com.microsoft.playwright.options.LoadState;
import com.wushi.module.market.domain.row.StockPlateDimensionRow;
import com.wushi.module.market.domain.row.StockPlateRelationSnapshotRow;
import com.wushi.module.spider.common.SpiderHttpClient;
import com.wushi.module.spider.eastmoney.KuaidailiProxyRefresher;
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
 * 特性: Playwright + stealth JS + 真实用户行为模拟 + 代理池 + 快代理IP自动刷新
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ThsPlaywrightSpiderServiceImpl {

    private static final String THS_HOME = "http://q.10jqka.com.cn/";
    private static final Pattern PLATE_CODE_PATTERN = Pattern.compile("/code/(\\d{6})");
    private static final Pattern STOCK_CODE_PATTERN = Pattern.compile("\\b(\\d{6})\\b");
    private static final int MAX_BLOCK_RETRIES = 3;

    private final SpiderHttpClient httpClient;
    private final ThsBrowserProperties browserProperties;
    private final ThsProxyProvider proxyProvider;
    private final KuaidailiProxyRefresher kuaidailiRefresher;
    private final AtomicBoolean running = new AtomicBoolean(false);

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

    public List<StockPlateDimensionRow> fetchAllPlates(LocalDate tradeDate) {
        List<StockPlateDimensionRow> allPlates = new ArrayList<>();
        String[][] categories = {
                {"gn", "CONCEPT"},
                {"dy", "REGION"},
                {"thshy", "INDUSTRY"},
                {"zjhhy", "CSRC_INDUSTRY"}
        };
        for (String[] cat : categories) {
            try {
                String url = THS_HOME + cat[0] + "/";
                List<StockPlateDimensionRow> plates = fetchPlatesFromPageWithRetry(url, cat[1], tradeDate);
                allPlates.addAll(plates);
                log.info("同花顺Playwright {}板块抓取完成: count={}", cat[0], plates.size());
            } catch (Exception e) {
                log.warn("同花顺Playwright {}板块抓取失败: {}", cat[0], e.getMessage());
            }
            humanDelay(1000, 2000);
        }
        return allPlates;
    }

    /**
     * 从板块列表页抓取板块 (带IP被封自动换代理重试)
     */
    private List<StockPlateDimensionRow> fetchPlatesFromPageWithRetry(String url, String plateType, LocalDate tradeDate) {
        for (int attempt = 0; attempt < MAX_BLOCK_RETRIES; attempt++) {
            Playwright playwright = null;
            Browser browser = null;
            try {
                playwright = Playwright.create();
                browser = createBrowser(playwright);
                BrowserContext context = createContext(browser);
                Page page = context.newPage();

                page.navigate(url, new Page.NavigateOptions().setTimeout(60000));
                page.waitForLoadState(LoadState.NETWORKIDLE);
                humanScroll(page);
                humanDelay(500, 1500);

                if (detectCaptcha(page)) {
                    log.warn("同花顺Playwright检测到验证码, attempt={}, url={}", attempt + 1, url);
                    if (kuaidailiRefresher.isEnabled() && attempt < MAX_BLOCK_RETRIES - 1) {
                        String newIp = kuaidailiRefresher.refreshOnBlock();
                        if (newIp != null) {
                            log.info("快代理已换新IP重试: {}", newIp);
                            closeQuietly(context);
                            closeQuietly(browser);
                            closeQuietly(playwright);
                            continue;
                        }
                    }
                    handleCaptcha(page);
                }

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
                            plates.add(new StockPlateDimensionRow(
                                    matcher.group(1), text.trim(), plateType, null, "ACTIVE", "ths"));
                        }
                    } catch (Exception e) {
                        log.warn("提取板块链接失败: {}", e.getMessage());
                    }
                }
                return plates;
            } finally {
                closeQuietly(browser);
                closeQuietly(playwright);
            }
        }
        return List.of();
    }

    public List<StockPlateRelationSnapshotRow> fetchAllPlateRelations(LocalDate tradeDate) {
        Playwright playwright = null;
        Browser browser = null;
        try {
            playwright = Playwright.create();
            browser = createBrowser(playwright);
            BrowserContext context = createContext(browser);
            Page page = context.newPage();

            List<StockPlateRelationSnapshotRow> allRelations = new ArrayList<>();
            // 简化示例: 遍历概念板块
            String url = THS_HOME + "gn/";
            page.navigate(url, new Page.NavigateOptions().setTimeout(60000));
            page.waitForLoadState(LoadState.NETWORKIDLE);
            humanScroll(page);

            if (detectCaptcha(page)) {
                if (kuaidailiRefresher.isEnabled()) {
                    String newIp = kuaidailiRefresher.refreshOnBlock();
                    if (newIp != null) {
                        log.info("快代理换新IP后重试关系页: {}", newIp);
                        closeQuietly(context);
                        browser.close();
                        browser = createBrowser(playwright);
                        context = createContext(browser);
                        page = context.newPage();
                        page.navigate(url, new Page.NavigateOptions().setTimeout(60000));
                        page.waitForLoadState(LoadState.NETWORKIDLE);
                    }
                } else {
                    handleCaptcha(page);
                }
            }

            Locator plateLinks = page.locator("a[href*='/code/']");
            int plateCount = plateLinks.count();
            Set<String> visitedPlates = new HashSet<>();

            for (int i = 0; i < plateCount; i++) {
                try {
                    String href = plateLinks.nth(i).getAttribute("href");
                    if (href == null) continue;
                    Matcher matcher = PLATE_CODE_PATTERN.matcher(href);
                    if (!matcher.find()) continue;
                    String plateCode = matcher.group(1);
                    if (visitedPlates.contains(plateCode)) continue;
                    visitedPlates.add(plateCode);

                    List<StockPlateRelationSnapshotRow> relations =
                            fetchPlateStocks(context, plateCode, tradeDate);
                    allRelations.addAll(relations);
                    humanDelay(800, 1500);
                } catch (Exception e) {
                    log.warn("抓取板块个股失败: {}", e.getMessage());
                }
            }
            return allRelations;
        } finally {
            closeQuietly(browser);
            closeQuietly(playwright);
        }
    }

    private List<StockPlateRelationSnapshotRow> fetchPlateStocks(BrowserContext context, String plateCode, LocalDate tradeDate) {
        Page page = context.newPage();
        try {
            String url = THS_HOME + "code/" + plateCode + "/";
            page.navigate(url, new Page.NavigateOptions().setTimeout(60000));
            page.waitForLoadState(LoadState.NETWORKIDLE);
            humanScroll(page);
            humanDelay(500, 1500);

            if (detectCaptcha(page)) handleCaptcha(page);

            List<StockPlateRelationSnapshotRow> relations = new ArrayList<>();
            Locator rows = page.locator("table tbody tr");
            int count = rows.count();
            for (int i = 0; i < count; i++) {
                try {
                    List<String> cells = rows.nth(i).locator("td").allTextContents();
                    if (cells.size() >= 2) {
                        String stockCode = STOCK_CODE_PATTERN.matcher(cells.get(0)).results()
                                .map(m -> m.group(1)).findFirst().orElse(null);
                        if (stockCode != null) {
                            relations.add(new StockPlateRelationSnapshotRow(
                                    tradeDate, stockCode, cells.get(1).trim(), plateCode, null, null,
                                    "HISTORICAL_CRAWLED", new BigDecimal("0.8"), 0, "ths"));
                        }
                    }
                } catch (Exception e) {
                    log.warn("提取个股行失败: {}", e.getMessage());
                }
            }
            return relations;
        } catch (Exception e) {
            log.warn("抓取板块个股失败: plateCode={}, error={}", plateCode, e.getMessage());
            return List.of();
        } finally {
            page.close();
        }
    }

    private Browser createBrowser(Playwright playwright) {
        BrowserType.LaunchOptions launchOptions = new BrowserType.LaunchOptions()
                .setHeadless(browserProperties.isHeadless())
                .setArgs(browserProperties.getArguments());

        if (StringUtils.hasText(browserProperties.getRemoteUrl())) {
            return playwright.chromium().connect(browserProperties.getRemoteUrl());
        }
        return playwright.chromium().launch(launchOptions);
    }

    private BrowserContext createContext(Browser browser) {
        String proxy = selectProxy();
        Browser.NewContextOptions options = new Browser.NewContextOptions()
                .setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36")
                .setViewportSize(1920, 1080)
                .setLocale("zh-CN")
                .setTimezoneId("Asia/Shanghai");
        if (proxy != null) {
            options.setProxy(new com.microsoft.playwright.options.Proxy(proxy));
        }
        BrowserContext context = browser.newContext(options);
        context.addInitScript(
            "Object.defineProperty(navigator, 'webdriver', { get: () => undefined });" +
            "window.chrome = { runtime: {} };" +
            "const oq = window.navigator.permissions.query;" +
            "window.navigator.permissions.query = (p) => p.name === 'notifications' ? Promise.resolve({ state: Notification.permission }) : oq(p);" +
            "Object.defineProperty(navigator, 'plugins', { get: () => [1, 2, 3, 4, 5] });" +
            "Object.defineProperty(navigator, 'languages', { get: () => ['zh-CN', 'zh', 'en'] });"
        );
        return context;
    }

    private void humanScroll(Page page) {
        int scrollCount = ThreadLocalRandom.current().nextInt(2, 5);
        for (int i = 0; i < scrollCount; i++) {
            page.mouse().wheel(0, ThreadLocalRandom.current().nextInt(300, 800));
            humanDelay(200, 600);
        }
    }

    private boolean detectCaptcha(Page page) {
        try {
            String content = page.content();
            if (content == null) return false;
            String lower = content.toLowerCase();
            return lower.contains("captcha") || lower.contains("滑块") || lower.contains("验证")
                    || lower.contains("verify") || lower.contains("访问过于频繁");
        } catch (Exception e) { return false; }
    }

    private void handleCaptcha(Page page) {
        log.warn("检测到反爬验证, 尝试刷新...");
        humanDelay(3000, 5000);
        page.reload();
        page.waitForLoadState(LoadState.NETWORKIDLE);
        humanDelay(2000, 3000);
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
        if (proxies.isEmpty()) return null;
        return proxies.get(ThreadLocalRandom.current().nextInt(proxies.size()));
    }

    private void humanDelay(int minMs, int maxMs) {
        try { Thread.sleep(ThreadLocalRandom.current().nextInt(minMs, maxMs)); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }

    private void closeQuietly(AutoCloseable c) {
        if (c != null) { try { c.close(); } catch (Exception ignored) {} }
    }
}
