package com.wushi.module.spider.eastmoney;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wushi.module.spider.common.SpiderHttpClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 东财爬虫客户端 — 完全 HttpURLConnection + User-Agent: Mozilla/5.0
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class EastMoneySpiderClient {

  private static final int PAGE_SIZE = 100;
  private static final int MAX_RETRY = 10;

  private final SpiderHttpClient httpClient;
  private final ObjectMapper objectMapper;
  private final EastMoneyProxyProvider proxyProvider;
  private final EastMoneyProperties eastMoneyProperties;
  private final KuaidailiProxyRefresher kuaidailiRefresher;

  // ========== 核心抓取 ==========

  public int fetchAllStocks(LocalDate tradeDate, List<JsonNode> outRows) throws Exception {
    String template = EastMoneyEndpoint.ALL_STOCK.getUrlTemplate();
    return fetchAllPages(template, outRows, "stock_daily_kline");
  }

  public int fetchIndexKline(LocalDate tradeDate, List<JsonNode> outRows) throws Exception {
    String template = EastMoneyEndpoint.INDEX_KLINE.getUrlTemplate();
    return fetchAllPages(template, outRows, "index_daily_kline");
  }

  // ========== 分页抓取 ==========

  private int fetchAllPages(String template, List<JsonNode> outRows, String taskName) throws Exception {
    int total = 0;
    int totalPage = 1;

    for (int page = 1; page <= totalPage; page++) {
      String url = String.format(template, page, System.currentTimeMillis());
      // 去掉 cb 参数 (返回纯 JSON, 不用解析 JSONP)
      url = url.replaceAll("&cb=[^&]*", "");
      try {
        String body = fetchWithRetry(url);
        JsonNode root = objectMapper.readTree(body);

        if (root.path("data").isMissingNode() || root.path("data").isNull()) {
          log.warn("[{}] page {} data=null, 可能被反爬", taskName, page);
          page--;
          continue;
        }

        JsonNode data = root.get("data");
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

  // ========== 请求 ==========

  private String fetchWithRetry(String url) throws Exception {
    Exception lastException = null;
    for (int attempt = 0; attempt < MAX_RETRY; attempt++) {
      try {
        String body = httpClient.simpleGet(url);
        if (isBlockedOrEmpty(body)) {
          log.warn("[东财] attempt={} 被反爬或返回空", attempt + 1);
          continue;
        }
        return body;
      } catch (Exception e) {
        lastException = e;
        log.warn("[东财] attempt={} 异常: {}", attempt + 1, e.getMessage());
        humanDelay(500, 1500);
      }
    }
    throw new Exception("东财请求" + MAX_RETRY + "次全部失败: " + (lastException != null ? lastException.getMessage() : "unknown"));
  }

  // ========== 工具 ==========

  private boolean isBlockedOrEmpty(String body) {
    if (body == null || body.isBlank()) return true;
    String lower = body.toLowerCase();
    if (lower.contains("access denied") || lower.contains("403") || lower.contains("频繁")) return true;
    try {
      JsonNode root = objectMapper.readTree(body);
      return root.path("data").isMissingNode() || root.path("data").isNull();
    } catch (Exception e) {
      return false;
    }
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
