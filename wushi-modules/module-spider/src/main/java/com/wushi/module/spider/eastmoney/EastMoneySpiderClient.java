package com.wushi.module.spider.eastmoney;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wushi.module.spider.common.SpiderHttpClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.HttpCookie;
import java.net.URI;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 东财爬虫客户端 - 浏览器级模拟
 * 特性: Cookie管理 + 全量请求头 + JSON直解析 + 自动换代理
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class EastMoneySpiderClient {

  private static final int PAGE_SIZE = 100;
  private static final int MAX_RETRY = 10;
  private static final String EASTMONEY_HOME = "http://quote.eastmoney.com/";
  private static final Pattern COOKIE_PATTERN = Pattern.compile("([^=]+)=([^;]+)");

  private final SpiderHttpClient httpClient;
  private final ObjectMapper objectMapper;
  private final EastMoneyProxyProvider proxyProvider;
  private final EastMoneyProperties eastMoneyProperties;
  private final KuaidailiProxyRefresher kuaidailiRefresher;

  /** 会话 Cookie, 跨请求复用 */
  private final ThreadLocal<Map<String, String>> cookieJar = ThreadLocal.withInitial(HashMap::new);

  // ========== 核心抓取方法 ==========

  public int fetchAllStocks(LocalDate tradeDate, List<JsonNode> outRows) throws Exception {
    ensureCookies();
    String template = EastMoneyEndpoint.ALL_STOCK.getUrlTemplate();
    return fetchAllPages(template, tradeDate, outRows, "stock_daily_kline");
  }

  public int fetchIndexKline(LocalDate tradeDate, List<JsonNode> outRows) throws Exception {
    ensureCookies();
    String template = EastMoneyEndpoint.INDEX_KLINE.getUrlTemplate();
    return fetchAllPages(template, tradeDate, outRows, "index_daily_kline");
  }

  // ========== 分页抓取 ==========

  private int fetchAllPages(String template, LocalDate tradeDate, List<JsonNode> outRows, String taskName) throws Exception {
    int total = 0;
    int totalPage = 1;

    for (int page = 1; page <= totalPage; page++) {
      String url = String.format(template, page, System.currentTimeMillis());
      try {
        String body = fetchWithBrowserSimulation(url);
        JsonNode root = objectMapper.readTree(body);
        
        if (root.has("data") && root.get("data").isNull()) {
          log.warn("[{}] 第{}页 data=null, 可能被反爬, 换IP重试", taskName, page);
          refreshProxyAndCookies();
          page--;
          continue;
        }

        JsonNode data = root.path("data");
        total = data.path("total").asInt(total);
        totalPage = Math.max(1, (int) Math.ceil(total / (double) PAGE_SIZE));
        
        if (data.path("diff").isArray()) {
          data.path("diff").forEach(outRows::add);
        }
        
        log.info("[{}] page {}/{}, rows={}, total={}", taskName, page, totalPage, data.path("diff").size(), total);
        humanDelay(150, 300);
      } catch (Exception e) {
        log.error("[{}] page {} 失败: {}", taskName, page, e.getMessage());
        if (page == 1) throw e;
        break;
      }
    }
    return total;
  }

  // ========== 浏览器模拟请求 ==========

  private String fetchWithBrowserSimulation(String url) throws Exception {
    Exception lastException = null;

    for (int attempt = 0; attempt < MAX_RETRY; attempt++) {
      String proxy = selectProxyForAttempt();
      try {
        Map<String, String> headers = buildFullHeaders();
        String body = httpClient.get(url, headers, proxy);

        // 检测反爬: 空数据或明确的 block 响应
        if (isBlockedOrEmpty(body)) {
          log.warn("[东财] attempt={} 被反爬或返回空, proxy={}", attempt + 1, proxyLabel(proxy));
          refreshProxyAndCookies();
          continue;
        }
        return body;
      } catch (Exception e) {
        lastException = e;
        log.warn("[东财] attempt={} 异常: {}, proxy={}", attempt + 1, e.getMessage(), proxyLabel(proxy));
        refreshProxyAndCookies();
      }
    }
    throw new Exception("东财请求" + MAX_RETRY + "次全部失败: " + (lastException != null ? lastException.getMessage() : "unknown"));
  }

  /** 构建完整的浏览器请求头 */
  /**
   * 构建极简浏览器头 — 只发 Chrome 一定会发的核心头
   * 关键: 不带 br (Java 不解码), 不带 Sec-Fetch-* (非浏览器标志)
   */
  private Map<String, String> buildFullHeaders() {
    Map<String, String> headers = new LinkedHashMap<>();
    headers.put("User-Agent", randomUserAgent());
    headers.put("Accept", "*/*");
    headers.put("Accept-Language", "zh-CN,zh;q=0.9");
    headers.put("Accept-Encoding", "gzip, deflate");
    headers.put("Referer", "http://quote.eastmoney.com/");
    
    // Cookie
    Map<String, String> cookies = cookieJar.get();
    if (!cookies.isEmpty()) {
      String cookieStr = cookies.entrySet().stream()
          .map(e -> e.getKey() + "=" + e.getValue())
          .reduce((a, b) -> a + "; " + b)
          .orElse("");
      headers.put("Cookie", cookieStr);
    }
    
    return headers;
  }

  // ========== Cookie 管理 ==========

  /** 确保有 Cookie, 如果没有就去首页拿 */
  private void ensureCookies() {
    if (cookieJar.get().isEmpty()) {
      refreshCookies();
      // 东财反爬: 首页→API 至少间隔 2 秒, 否则判为机器
      humanDelay(2000, 3000);
    }
  }

  /** 访问首页获取 Cookie */
  private void refreshCookies() {
    try {
      Map<String, String> headers = new LinkedHashMap<>();
      headers.put("User-Agent", randomUserAgent());
      headers.put("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8");
      headers.put("Accept-Language", "zh-CN,zh;q=0.9");
      
      String proxy = selectProxyForAttempt();
      // 用 RestTemplate 的 exchange 方法拿到响应头
      var response = httpClient.getWithHeaders(EASTMONEY_HOME, headers, proxy);
      extractAndStoreCookies(response);
      log.info("[Cookie] 刷新成功, cookies={}", cookieJar.get().keySet());
    } catch (Exception e) {
      log.warn("[Cookie] 刷新失败: {}", e.getMessage());
    }
  }

  /** 从响应头提取 Cookie */
  private void extractAndStoreCookies(Map<String, String> responseHeaders) {
    Map<String, String> cookies = cookieJar.get();
    cookies.clear();
    for (Map.Entry<String, String> entry : responseHeaders.entrySet()) {
      if ("Set-Cookie".equalsIgnoreCase(entry.getKey()) || "set-cookie".equalsIgnoreCase(entry.getKey())) {
        // Set-Cookie: name=value; path=/; HttpOnly
        String[] parts = entry.getValue().split(";");
        for (String part : parts) {
          part = part.trim();
          int eq = part.indexOf("=");
          if (eq > 0) {
            String name = part.substring(0, eq).trim();
            String value = part.substring(eq + 1).trim();
            if (!name.isEmpty() && !value.isEmpty()) {
              cookies.put(name, value);
            }
          }
        }
      }
    }
  }

  /** 换代理 + 刷新 Cookie */
  private void refreshProxyAndCookies() {
    if (kuaidailiRefresher.isEnabled()) {
      String newIp = kuaidailiRefresher.refreshOnBlock();
      if (newIp != null) {
        log.info("[快代理] 换新IP: {}", newIp);
        cookieJar.get().clear();
        ensureCookies();
        return;
      }
    }
    // fallback: 静态代理池选一个
    String proxy = selectProxy();
    if (proxy != null) {
      kuaidailiRefresher.reportFailed(proxy);
    }
    cookieJar.get().clear();
    ensureCookies();
  }

  // ========== 代理选择 ==========

  private String selectProxyForAttempt() {
    if (kuaidailiRefresher.isEnabled()) {
      String proxy = kuaidailiRefresher.nextProxy();
      if (proxy != null) return proxy;
    }
    return selectProxy();
  }

  private String selectProxy() {
    List<String> proxies = proxyProvider.availableProxies();
    if (proxies.isEmpty()) return null;
    return proxies.get(ThreadLocalRandom.current().nextInt(proxies.size()));
  }

  private String proxyLabel(String proxy) {
    return proxy == null || proxy.isBlank() ? "DIRECT" : proxy.substring(0, Math.min(proxy.length(), 20)) + "...";
  }

  // ========== 反爬检测 ==========

  private boolean isBlockedOrEmpty(String body) {
    if (body == null || body.isBlank()) return true;
    String lower = body.toLowerCase();
    if (lower.contains("access denied") || lower.contains("403") || lower.contains("频繁") || lower.contains("too many")) return true;
    // 尝试解析 JSON, 检查 data 是否存在
    try {
      JsonNode root = objectMapper.readTree(body);
      JsonNode data = root.path("data");
      return data.isMissingNode() || data.isNull();
    } catch (Exception e) {
      return false;
    }
  }

  // ========== UA 工具 ==========

  private static final List<String> USER_AGENTS = Arrays.asList(
      "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36",
      "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36",
      "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36"
  );

  private String randomUserAgent() {
    return USER_AGENTS.get(ThreadLocalRandom.current().nextInt(USER_AGENTS.size()));
  }

  private void humanDelay(int minMs, int maxMs) {
    try {
      Thread.sleep(ThreadLocalRandom.current().nextInt(minMs, maxMs));
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }

  // ========== 兼容旧接口 ==========

  public EastMoneyPageResult fetchPaged(EastMoneyEndpoint endpoint) {
    List<JsonNode> rows = new ArrayList<>();
    try {
      int total = fetchAllStocks(LocalDate.now(), rows);
      return new EastMoneyPageResult(total, rows);
    } catch (Exception e) {
      log.error("fetchPaged 失败", e);
      return new EastMoneyPageResult(0, List.of());
    }
  }

  public EastMoneyPoolResult fetchPool(EastMoneyEndpoint endpoint, LocalDate tradeDate) {
    return new EastMoneyPoolResult(0, List.of());
  }

  public EastMoneyPageResult fetchPlateStocks(String plateCode) {
    return new EastMoneyPageResult(0, List.of());
  }

  public record EastMoneyPageResult(int totalCount, List<JsonNode> rows) {}
  public record EastMoneyPoolResult(int totalCount, List<JsonNode> rows) {}
}
