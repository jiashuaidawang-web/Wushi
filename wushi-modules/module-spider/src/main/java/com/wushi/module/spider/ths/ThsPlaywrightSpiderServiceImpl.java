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
 * 特性: Playwright + stealth JS + 真实用户行为模拟 + 代理池
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
                List<StockPlateDimensionRow> plates = fetchPlatesFromPage(url, cat[1], tradeDate);
                allPlates.addAll(plates);
                log.info("同花顺Playwright {}板块抓取完成: count={}", cat[0], plates.size());
            } catch (Exception e) {
                log.warn("同花顺Playwright {}板块抓取失败: {}", cat[0], e.getMessage());
            }
            humanDelay(1000, 2000);
        }
        return allPlates;
    }

    public List<StockPlateDimensionRow> fetchPlatesFromPage(String url, String plateType, LocalDate tradeDate) {
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
                log.warn("同花顺Playwright检测到验证码, url={}", url);
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

    public List<StockPlateRelationSnapshotRow> fetchAllPlateRelations(LocalDate tradeDate) {
        List<StockPlateRelationSnapshotRow> allRelations = new ArrayList<>();
        List<StockPlateDimensionRow> plates = fetchAllPlates(tradeDate);
        for (StockPlateDimensionRow plate : plates) {
            try {
                List<StockPlateRelationSnapshotRow> relations = fetchPlateStocks(plate.plateCode(), tradeDate);
                allRelations.addAll(relations);
                log.debug("同花顺Playwright板块个股抓取: plateCode={}, count={}", plate.plateCode(), relations.size());
            } catch (Exception e) {
                log.warn("同花顺Playwright板块个股抓取失败: plateCode={}, error={}", plate.plateCode(), e.getMessage());
            }
            humanDelay(800, 1500);
        }
        return allRelations;
    }

    public List<StockPlateRelationSnapshotRow> fetchPlateStocks(String plateCode, LocalDate tradeDate) {
        Playwright playwright = null;
        Browser browser = null;
        try {
            playwright = Playwright.create();
            browser = createBrowser(playwright);
            BrowserContext context = createContext(browser);
            Page page = context.newPage();

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
        } finally {
            closeQuietly(browser);
            closeQuietly(playwright);
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

    private String selectProxy() {
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
