package com.wushi.module.spider.eastmoney;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.playwright.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import jakarta.annotation.PreDestroy;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

@Slf4j
@Component
public class EastMoneyPlaywrightClient {

    private static final int MAX_PAGE_RETRIES = 3;
    private static final int RETRY_BASE_DELAY_MS = 2000;
    private static final int PAGE_DELAY_MIN_MS = 200;
    private static final int PAGE_DELAY_MAX_MS = 500;

    private final ObjectMapper objectMapper;
    private Playwright playwright;
    private Browser browser;
    private BrowserContext context;

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

    private BrowserContext getOrCreateContext() {
        if (context == null) {
            newContext();
        }
        return context;
    }

    private void newContext() {
        Browser.NewContextOptions opts = new Browser.NewContextOptions()
            .setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36")
            .setViewportSize(1920, 1080)
            .setLocale("zh-CN")
            .setTimezoneId("Asia/Shanghai");
        opts.setProxy("http://f278.kdltpspro.com:15818");
        opts.setHttpCredentials("t18377527660878", "oyu11md5");
        context = browser.newContext(opts);
        context.addInitScript(
            "Object.defineProperty(navigator, 'webdriver', { get: () => undefined });" +
            "window.chrome = { runtime: {} };"
        );
        log.info("[Playwright] 浏览器上下文已创建/刷新 (with proxy)");
    }

    private void refreshSession() {
        if (context != null) {
            try { context.close(); } catch (Exception ignored) {}
            context = null;
        }
        newContext();
    }

    // ========== 分页抓取 ==========

    /**
     * 抓取全市场股票日K (ALL_STOCK)
     */
    public int fetchAllStocks(List<JsonNode> outRows) throws Exception {
        return fetchAllPages(EastMoneyEndpoint.ALL_STOCK, outRows, "stock_daily_kline");
    }

    /**
     * 通用分页抓取 - 支持任意 EastMoney endpoint
     * 返回实际抓取到的行数（不是 API 声明的 total）
     */
    public int fetchAllPages(EastMoneyEndpoint endpoint, List<JsonNode> outRows, String taskName) throws Exception {
        String template = endpoint.getUrlTemplate();
        int apiTotal = 0;
        int totalPage = 1;
        int fetchedRows = 0;

        for (int page = 1; page <= totalPage; page++) {
            String url = String.format(template, page, System.currentTimeMillis());
            url = url.replaceAll("[?&]cb=[^&]*", "");
            url = url.replaceAll("\\?&", "?").replaceAll("&&", "&").replaceAll("[?&]$", "");

            int attempt = 0;
            while (true) {
                attempt++;
                try {
                    String json = fetchPageJson(url);
                    JsonNode root = objectMapper.readTree(json);

                    if (root.path("data").isMissingNode() || root.path("data").isNull()) {
                        log.warn("[{}] page {} data=null (attempt={})", taskName, page, attempt);
                        if (attempt < MAX_PAGE_RETRIES) {
                            refreshSession();
                            sleep(RETRY_BASE_DELAY_MS * attempt);
                            continue;
                        }
                        log.error("[{}] page {} data=null 超过最大重试次数,跳过", taskName, page);
                        break;
                    }

                    JsonNode data = root.get("data");
                    apiTotal = data.path("total").asInt(0);
                    totalPage = Math.max(1, (int) Math.ceil(apiTotal / (double) 100));

                    if (data.path("diff").isArray()) {
                        int pageSize = data.path("diff").size();
                        data.path("diff").forEach(outRows::add);
                        fetchedRows += pageSize;
                        log.info("[{}] page {}/{}, rows={}, total={}, fetched={}", taskName, page, totalPage, pageSize, apiTotal, outRows.size());
                    } else {
                        log.info("[{}] page {}/{}, no diff array", taskName, page, totalPage);
                    }

                    randomDelay(PAGE_DELAY_MIN_MS, PAGE_DELAY_MAX_MS);
                    break;

                } catch (Exception e) {
                    log.warn("[{}] page {} attempt={} 失败: {}", taskName, page, attempt, e.getMessage());
                    if (attempt < MAX_PAGE_RETRIES) {
                        refreshSession();
                        sleep(RETRY_BASE_DELAY_MS * attempt);
                        continue;
                    }
                    log.error("[{}] page {} 超过最大重试次数,终止: {}", taskName, page, e.getMessage());
                    if (page == 1) throw e;
                    return fetchedRows;
                }
            }
        }
        return fetchedRows;
    }

    // ========== 非分页抓取 (Pool) ==========

    /**
     * 抓取股票池 (非分页 endpoint)
     */
    public int fetchPool(EastMoneyEndpoint endpoint, List<JsonNode> outRows, String taskName, Object... extraArgs) throws Exception {
        String template = endpoint.getUrlTemplate();
        Object[] args = new Object[extraArgs.length + 1];
        System.arraycopy(extraArgs, 0, args, 0, extraArgs.length);
        args[extraArgs.length] = System.currentTimeMillis();

        String url = String.format(template, args);
        url = url.replaceAll("[?&]cb=[^&]*", "");
        url = url.replaceAll("\\?&", "?").replaceAll("&&", "&").replaceAll("[?&]$", "");

        int attempt = 0;
        while (true) {
            attempt++;
            try {
                String json = fetchPageJson(url);
                JsonNode root = objectMapper.readTree(json);

                if (root.path("data").isArray()) {
                    root.path("data").forEach(outRows::add);
                }
                int size = root.path("data").size();
                log.info("[{}] pool rows={}", taskName, size);
                return size;
            } catch (Exception e) {
                log.warn("[{}] pool attempt={} 失败: {}", taskName, attempt, e.getMessage());
                if (attempt < MAX_PAGE_RETRIES) {
                    refreshSession();
                    sleep(RETRY_BASE_DELAY_MS * attempt);
                    continue;
                }
                log.error("[{}] pool 超过最大重试次数", taskName);
                throw e;
            }
        }
    }

    // ========== 底层请求 ==========

    private String fetchPageJson(String url) {
        BrowserContext ctx = getOrCreateContext();
        Page page = ctx.newPage();
        int attempt = 0;
        while (true) {
            attempt++;
            try {
                Response response = page.navigate(url, new Page.NavigateOptions().setTimeout(30000));
                if (response == null) {
                    throw new RuntimeException("attempt=" + attempt + " response is null");
                }
                String text = response.text();
                if (text == null || text.isBlank()) {
                    throw new RuntimeException("attempt=" + attempt + " response body is empty");
                }

                // 去掉 JSONP: jQuery123( ... )
                text = text.trim();
                int p0 = text.indexOf('(');
                int p1 = text.lastIndexOf(')');
                if (p0 >= 0 && p1 > p0) text = text.substring(p0 + 1, p1);
                return text.trim();
            } catch (Exception e) {
                log.warn("[fetchPageJson] attempt={} 失败: {}", attempt, e.getMessage());
                if (attempt < MAX_PAGE_RETRIES) {
                    refreshSession();
                    ctx = getOrCreateContext();
                    page = ctx.newPage();
                    sleep(RETRY_BASE_DELAY_MS * attempt);
                    continue;
                }
                throw e;
            } finally {
                try { page.close(); } catch (Exception ignored) {}
            }
        }
    }

    private void randomDelay(int minMs, int maxMs) {
        try {
            Thread.sleep(ThreadLocalRandom.current().nextInt(minMs, maxMs));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @PreDestroy
    public void shutdown() {
        if (browser != null) {
            try { browser.close(); } catch (Exception ignored) {}
        }
        if (playwright != null) {
            try { playwright.close(); } catch (Exception ignored) {}
        }
        log.info("[Playwright] 浏览器已关闭");
    }
}
