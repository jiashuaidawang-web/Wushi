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
 * 东财 Playwright 浏览器客户端
 * 通过隧道代理 + Playwright 模拟浏览器抓取东财公开接口
 *
 * <p>容错设计:
 * <ul>
 *   <li>每页最多重试 {@link #MAX_PAGE_RETRIES} 次, 采用指数退避 (1s, 2s, 4s, 8s...)</li>
 *   <li>遇到 ERR_EMPTY_RESPONSE / TargetClosedError 等浏览器崩溃时, 自动重建浏览器上下文</li>
 *   <li>每 {@link #CONTEXT_REFRESH_INTERVAL} 页强制刷新上下文, 防止代理 IP 过载</li>
 * </ul>
 *
 * <p><b>线程安全</b>: 本组件是 Spring 单例 Bean, 所有浏览器操作方法使用 {@code synchronized} 保证串行访问。
 */
@Slf4j
@Component
public class EastMoneyPlaywrightClient {

    private static final int MAX_PAGE_RETRIES = 4;
    private static final long RETRY_BASE_DELAY_MS = 1000L;
    private static final int CONTEXT_REFRESH_INTERVAL = 20;
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

    // ========== 浏览器生命周期 ==========

    private synchronized void initBrowser() {
        try {
            if (playwright == null) {
                this.playwright = Playwright.create();
            }
            if (browser != null) {
                try { browser.close(); } catch (Exception ignored) {}
            }
            this.browser = playwright.chromium().launch(
                new BrowserType.LaunchOptions()
                    .setHeadless(true)
                    .setArgs(Arrays.asList(
                        "--disable-blink-features=AutomationControlled",
                        "--disable-dev-shm-usage",
                        "--no-sandbox",
                        "--disable-gpu",
                        "--window-size=1920,1080",
                        "--disable-extensions",
                        "--disable-background-networking"
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

    private synchronized void newContext() {
        Browser.NewContextOptions opts = new Browser.NewContextOptions()
            .setUserAgent("Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/150.0.0.0 Safari/537.36")
            .setViewportSize(1920, 1080)
            .setLocale("zh-CN")
            .setTimezoneId("Asia/Shanghai");
        // 快代理已过期,改为本机直连
        if (browser == null) {
            initBrowser();
        }
        context = browser.newContext(opts);
        // 关键:东财反爬识别 Referer + Cookie,必须带真实 Header
        Map<String,String> headers = new HashMap<>();
        headers.put("Referer", "https://quote.eastmoney.com/center/gridlist.html");
        headers.put("Origin", "https://quote.eastmoney.com");
        headers.put("Host", "push2.eastmoney.com");
        headers.put("sec-fetch-dest", "script");
        headers.put("sec-fetch-mode", "no-cors");
        headers.put("sec-fetch-site", "same-site");
        context.setExtraHTTPHeaders(headers);
        // Cookie 自动管理:先访问主站种 Cookie,后续 API 请求自动带上
        warmupForSite(context, "https://quote.eastmoney.com/center/gridlist.html");
        log.info("[Playwright] 已注入反爬 Header(Referer/Origin/Sec-Fetch) + Cookie 预热完成");
        context.addInitScript(
            "Object.defineProperty(navigator, 'webdriver', { get: () => undefined });"
            + "window.chrome = { runtime: {} };"
        );
        log.info("[Playwright] 浏览器上下文已创建/刷新 (with proxy)");
    }

    /**
     * Cookie 预热:访问指定站点种 Session Cookie,后续 API 请求自动带上
     * 金融站点每次响应都会刷新 Cookie,Playwright 自动更新 Cookie Jar
     *
     * @param siteUrl 要预热的主站 URL
     */
    public static void warmupForSite(BrowserContext ctx, String siteUrl) {
        Page page = ctx.newPage();
        try {
            page.navigate(siteUrl, new Page.NavigateOptions().setTimeout(15000));
            page.waitForLoadState(LoadState.DOMCONTENTLOADED);
            log.info("[Playwright] Cookie 预热完成: {}", siteUrl);
        } catch (Exception e) {
            log.warn("[Playwright] Cookie 预热失败(不影响后续): {}", e.getMessage());
        } finally {
            page.close();
        }
    }

    /**
     * 浏览器崩溃恢复: 关闭并重建整个浏览器进程 + 上下文
     */
    private synchronized void recoverBrowser() {
        log.warn("[Playwright] 浏览器崩溃, 执行恢复: 关闭并重建");
        try { if (context != null) context.close(); } catch (Exception ignored) {}
        context = null;
        try { if (browser != null) browser.close(); } catch (Exception ignored) {}
        browser = null;
        initBrowser();
        newContext();
    }

    /**
     * 指数退避延时: 第 n 次重试等待 BASE * 2^(n-1) 毫秒
     * <p>第1次重试: 1s, 第2次: 2s, 第3次: 4s, 第4次: 8s ...
     */
    private static long backoffDelayMs(int retryCount) {
        return RETRY_BASE_DELAY_MS * (1L << (retryCount - 1));
    }

    // ========== 分页抓取 ==========

    public int fetchAllStocks(List<JsonNode> outRows) throws Exception {
        return fetchAllPages(EastMoneyEndpoint.ALL_STOCK, outRows, "stock_daily_kline");
    }

    /**
     * 通用分页抓取
     *
     * @return 实际抓取到的行数
     */
    public int fetchAllPages(EastMoneyEndpoint endpoint, List<JsonNode> outRows, String taskName)
            throws Exception {
        String template = endpoint.getUrlTemplate();
        int totalPage = 1;

        for (int page = 1; page <= totalPage; page++) {
            // 定期刷新上下文, 防止代理 IP 过载被封
            if (page > 1 && page % CONTEXT_REFRESH_INTERVAL == 0) {
                log.info("[{}] page {}, 定期刷新上下文(防代理过载)", taskName, page);
                refreshSession();
            }

            String url = buildUrl(template, page);
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
                            sleep(backoffDelayMs(attempt));
                            continue;
                        }
                        log.error("[{}] page {} data=null 超过最大重试次数,跳过", taskName, page);
                        break;
                    }

                    JsonNode data = root.get("data");
                    int apiTotal = data.path("total").asInt(0);
                    totalPage = Math.max(1, (int) Math.ceil(apiTotal / (double) 100));

                    if (data.path("diff").isArray()) {
                        int pageSize = data.path("diff").size();
                        data.path("diff").forEach(outRows::add);
                        log.info("[{}] page {}/{}, rows={}, fetched={}",
                            taskName, page, totalPage, pageSize, outRows.size());
                    }

                    randomDelay(PAGE_DELAY_MIN_MS, PAGE_DELAY_MAX_MS);
                    break;

                } catch (Exception e) {
                    boolean isCrash = isBrowserCrash(e);
                    log.warn("[{}] page {} attempt={} 失败: {}{}",
                        taskName, page, attempt, e.getMessage(), isCrash ? " [浏览器崩溃]" : "");

                    if (attempt < MAX_PAGE_RETRIES) {
                        if (isCrash) {
                            recoverBrowser();
                        } else {
                            refreshSession();
                        }
                        sleep(backoffDelayMs(attempt));
                        continue;
                    }
                    log.error("[{}] page {} 超过最大重试次数({}次),终止", taskName, page, MAX_PAGE_RETRIES);
                    if (page == 1) {
                        throw new RuntimeException("第1页连续" + MAX_PAGE_RETRIES + "次抓取失败, 终止任务", e);
                    }
                    return outRows.size();
                }
            }
        }
        return outRows.size();
    }

    // ========== 非分页抓取 (Pool) ==========

    public int fetchPool(EastMoneyEndpoint endpoint, List<JsonNode> outRows, String taskName,
                         Object... extraArgs) throws Exception {
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
                boolean isCrash = isBrowserCrash(e);
                log.warn("[{}] pool attempt={} 失败: {}", taskName, attempt, e.getMessage());
                if (attempt < MAX_PAGE_RETRIES) {
                    if (isCrash) { recoverBrowser(); } else { refreshSession(); }
                    sleep(backoffDelayMs(attempt));
                    continue;
                }
                log.error("[{}] pool 超过最大重试次数", taskName);
                throw e;
            }
        }
    }

    // ========== 底层请求 ==========

    /**
     * 抓取单个页面并返回 JSON 文本
     *
     * <p>使用 navigate 同步返回 Response, 立即读取 body 后再 close page,
     * 避免 finally 与回调竞争导致 TargetClosedError.
     */
    private String fetchPageJson(String url) {
        BrowserContext ctx = getOrCreateContext();
        Page page = ctx.newPage();
        try {
            Response response = page.navigate(url, new Page.NavigateOptions().setTimeout(30000));
            if (response == null) {
                throw new RuntimeException("response is null");
            }
            String text = response.text();
            if (text == null || text.isBlank()) {
                throw new RuntimeException("response body is empty");
            }
            // 去掉 JSONP 包装: jQuery123( ... ) -> ...
            text = text.trim();
            int p0 = text.indexOf('(');
            int p1 = text.lastIndexOf(')');
            if (p0 >= 0 && p1 > p0) {
                text = text.substring(p0 + 1, p1);
            }
            return text.trim();
        } finally {
            try { page.close(); } catch (Exception ignored) {}
        }
    }

    // ========== 工具方法 ==========

    private String buildUrl(String template, int page) {
        String url = String.format(template, page, System.currentTimeMillis());
        url = url.replaceAll("[?&]cb=[^&]*", "");
        url = url.replaceAll("\\?&", "?").replaceAll("&&", "&").replaceAll("[?&]$", "");
        return url;
    }

    private boolean isBrowserCrash(Exception e) {
        if (e.getMessage() == null) return false;
        String msg = e.getMessage();
        return msg.contains("has been closed")
            || msg.contains("Target closed")
            || msg.contains("Browser closed")
            || msg.contains("Session closed");
    }

    private void refreshSession() {
        if (context != null) {
            try { context.close(); } catch (Exception ignored) {}
            context = null;
        }
        newContext();
    }

    private void randomDelay(int minMs, int maxMs) {
        sleep(ThreadLocalRandom.current().nextInt(minMs, maxMs));
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @PreDestroy
    public void shutdown() {
        if (context != null) {
            try { context.close(); } catch (Exception ignored) {}
        }
        if (browser != null) {
            try { browser.close(); } catch (Exception ignored) {}
        }
        if (playwright != null) {
            try { playwright.close(); } catch (Exception ignored) {}
        }
        log.info("[Playwright] 浏览器已关闭");
    }
}
