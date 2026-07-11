package com.wushi.module.spider.eastmoney;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.playwright.*;
import com.microsoft.playwright.options.LoadState;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import jakarta.annotation.PreDestroy;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 东财浏览器级爬虫 - Playwright 真实 Chromium
 * 让东财完全感知不到是爬虫
 */
@Slf4j
@Component
public class EastMoneyPlaywrightClient {

    private static final String EASTMONEY_HOME = "http://quote.eastmoney.com/";
    private final ObjectMapper objectMapper;
    private Playwright playwright;
    private Browser browser;

    public EastMoneyPlaywrightClient(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        initBrowser();
    }

    private void initBrowser() {
        try {
            this.playwright = Playwright.create();
            this.browser = playwright.chromium().launch(
                new BrowserType.LaunchOptions()
                    .setHeadless(true)
                    .setArgs(Arrays.asList(
                        "--disable-blink-features=AutomationControlled",
                        "--disable-dev-shm-usage",
                        "--no-sandbox",
                        "--disable-gpu",
                        "--window-size=1920,1080"
                    ))
            );
            log.info("[Playwright] Chromium 启动成功");
        } catch (Exception e) {
            log.error("[Playwright] 启动失败: {}", e.getMessage(), e);
            throw e;
        }
    }

    /**
     * 抓取全市场股票日K
     */
    public int fetchAllStocks(List<JsonNode> outRows) throws Exception {
        ensureSession();
        String template = EastMoneyEndpoint.ALL_STOCK.getUrlTemplate();
        return fetchAllPages(template, outRows, "stock_daily_kline");
    }

    private int fetchAllPages(String template, List<JsonNode> outRows, String taskName) throws Exception {
        int total = 0;
        int totalPage = 1;

        for (int page = 1; page <= totalPage; page++) {
            String url = String.format(template, page, System.currentTimeMillis());
            url = url.replaceAll("&cb=[^&]*", "");

            try {
                String json = fetchPageJson(url);
                JsonNode root = objectMapper.readTree(json);

                if (root.path("data").isMissingNode() || root.path("data").isNull()) {
                    log.warn("[{}] page {} data=null, 刷新Session重试", taskName, page);
                    refreshSession();
                    page--;
                    continue;
                }

                JsonNode data = root.get("data");
                total = data.path("total").asInt(total);
                totalPage = Math.max(1, (int) Math.ceil(total / (double) 100));

                if (data.path("diff").isArray()) {
                    data.path("diff").forEach(outRows::add);
                }

                log.info("[{}] page {}/{}, rows={}, total={}", taskName, page, totalPage, data.path("diff").size(), total);
                randomDelay(150, 300);
            } catch (Exception e) {
                log.error("[{}] page {} 失败: {}", taskName, page, e.getMessage());
                if (page == 1) throw e;
                break;
            }
        }
        return total;
    }

    private String fetchPageJson(String url) {
        BrowserContext context = getOrCreateContext();
        Page page = context.newPage();
        try {
            page.navigate(url, new Page.NavigateOptions().setTimeout(30000));
            page.waitForLoadState(LoadState.NETWORKIDLE);
            String content = page.content();
            // 提取 pre 标签里的 JSON
            Locator pre = page.locator("pre");
            if (pre.count() > 0) {
                String text = pre.first().textContent();
                if (text != null && !text.isBlank()) {
                    return text.trim();
                }
            }
            return content;
        } finally {
            page.close();
        }
    }

    private BrowserContext context;

    private BrowserContext getOrCreateContext() {
        if (context == null) {
            context = browser.newContext(new Browser.NewContextOptions()
                .setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36")
                .setViewportSize(1920, 1080)
                .setLocale("zh-CN")
                .setTimezoneId("Asia/Shanghai"));
            // 注入 stealth JS — 移除 webdriver 标记
            context.addInitScript(
                "Object.defineProperty(navigator, 'webdriver', { get: () => undefined });" +
                "window.chrome = { runtime: {} };" +
                "delete navigator.__proto__.connection;"
            );
        }
        return context;
    }

    private void ensureSession() {
        if (context == null) {
            // 先访问首页建立会话
            BrowserContext ctx = getOrCreateContext();
            Page page = ctx.newPage();
            try {
                page.navigate(EASTMONEY_HOME, new Page.NavigateOptions().setTimeout(30000));
                page.waitForLoadState(LoadState.NETWORKIDLE);
                randomDelay(2000, 3000);
                log.info("[Playwright] 首页会话建立成功");
            } catch (Exception e) {
                log.warn("[Playwright] 首页访问失败: {}", e.getMessage());
            } finally {
                page.close();
            }
        }
    }

    private void refreshSession() {
        if (context != null) {
            context.close();
            context = null;
        }
        ensureSession();
    }

    private void randomDelay(int minMs, int maxMs) {
        try {
            Thread.sleep(ThreadLocalRandom.current().nextInt(minMs, maxMs));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @PreDestroy
    public void shutdown() {
        if (browser != null) browser.close();
        if (playwright != null) playwright.close();
        log.info("[Playwright] 浏览器已关闭");
    }
}
