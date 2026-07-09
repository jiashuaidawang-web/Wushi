package com.wushi.module.spider.eastmoney;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wushi.module.spider.common.SpiderHttpClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 东财爬虫客户端 - 带代理池轮换
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class EastMoneySpiderClient {

    private static final int PAGE_SIZE = 100;
    private static final DateTimeFormatter BASIC_DATE = DateTimeFormatter.BASIC_ISO_DATE;
    private static final int MAX_RETRY = 3;
    private static final Pattern JSONP = Pattern.compile("(?:^|\\s)(\\w+)\\s*\\((.*)\\)\\s*;?\\s*$", Pattern.DOTALL);

    private final SpiderHttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final EastMoneyProxyProvider proxyProvider;
    private final EastMoneyProperties eastMoneyProperties;

    public EastMoneyPageResult fetchPaged(EastMoneyEndpoint endpoint) {
        return fetchPaged(endpoint, MAX_RETRY);
    }

    private EastMoneyPageResult fetchPaged(EastMoneyEndpoint endpoint, int maxRetry) {
        int total = 0;
        List<JsonNode> allRows = new ArrayList<>();
        int totalPage = 1;

        for (int page = 1; page <= totalPage; page++) {
            String url = String.format(endpoint.getUrlTemplate(), page, System.currentTimeMillis());
            try {
                String body = fetchWithRetry(url, maxRetry);
                JsonNode root = parseJsonp(body);
                JsonNode data = root.path("data");
                total = data.path("total").asInt(total);
                totalPage = Math.max(1, (int) Math.ceil(total / (double) PAGE_SIZE));
                if (data.path("diff").isArray()) {
                    data.path("diff").forEach(allRows::add);
                }
                log.debug("Fetched {} page {}/{}, rows={}", endpoint.name(), page, totalPage, data.path("diff").size());
                humanDelay(150, 300);
            } catch (Exception e) {
                log.error("东财分页接口请求失败: endpoint={}, page={}, error={}", endpoint.name(), page, e.getMessage());
                break;
            }
        }
        return new EastMoneyPageResult(total, allRows);
    }

    public EastMoneyPoolResult fetchPool(EastMoneyEndpoint endpoint, LocalDate tradeDate) {
        return fetchPool(endpoint, tradeDate, MAX_RETRY);
    }

    private EastMoneyPoolResult fetchPool(EastMoneyEndpoint endpoint, LocalDate tradeDate, int maxRetry) {
        String url = String.format(endpoint.getUrlTemplate(), tradeDate.format(BASIC_DATE), System.currentTimeMillis());
        try {
            String body = fetchWithRetry(url, maxRetry);
            JsonNode root = parseJsonp(body);
            JsonNode data = root.path("data");
            int total = data.path("tc").asInt(0);
            List<JsonNode> rows = new ArrayList<>();
            if (data.path("pool").isArray()) {
                data.path("pool").forEach(rows::add);
            }
            return new EastMoneyPoolResult(total, rows);
        } catch (Exception e) {
            log.error("东财股票池接口请求失败: endpoint={}, date={}, error={}", endpoint.name(), tradeDate, e.getMessage());
            return new EastMoneyPoolResult(0, List.of());
        }
    }

    public EastMoneyPageResult fetchPlateStocks(String plateCode) {
        return fetchPlateStocks(plateCode, MAX_RETRY);
    }

    private EastMoneyPageResult fetchPlateStocks(String plateCode, int maxRetry) {
        String bkNumber = plateCode == null ? "" : plateCode.replace("BK", "");
        String template = "https://push2.eastmoney.com/api/qt/clist/get?np=1&fltt=2&invt=2&cb=jQuery37103197788499441794_%d&fs=b:BK%s+f:!50&fields=f12,f13,f14,f1,f2,f4,f3,f152,f5,f6,f7,f15,f18,f16,f17,f10,f8,f9,f23&fid=f3&pn=%d&pz=100&po=1&dect=1&ut=fa5fd1943c7b386f172d6893dbfba10b&_=%d";

        int total = 0;
        List<JsonNode> allRows = new ArrayList<>();
        int totalPage = 1;
        long cb = System.currentTimeMillis();

        for (int page = 1; page <= totalPage; page++) {
            String url = String.format(template, cb, bkNumber, page, System.currentTimeMillis());
            try {
                String body = fetchWithRetry(url, maxRetry);
                JsonNode root = parseJsonp(body);
                JsonNode data = root.path("data");
                if (data.isMissingNode() || data.isNull()) break;
                total = data.path("total").asInt(total);
                totalPage = Math.max(1, (int) Math.ceil(total / (double) PAGE_SIZE));
                if (data.path("diff").isArray()) {
                    data.path("diff").forEach(allRows::add);
                }
                humanDelay(150, 300);
            } catch (Exception e) {
                log.error("东财板块个股请求失败: plateCode={}, page={}, error={}", plateCode, page, e.getMessage());
                break;
            }
        }
        return new EastMoneyPageResult(total, allRows);
    }

    // ========== 代理+重试 ==========

    private String fetchWithRetry(String url, int maxRetry) throws Exception {
        Exception lastException = null;

        for (int attempt = 0; attempt < maxRetry; attempt++) {
            String proxy = selectProxy();
            try {
                Map<String, String> headers = eastMoneyHeaders();
                String body;
                if (proxy != null) {
                    body = httpClient.get(url, headers, proxy);
                } else {
                    body = httpClient.get(url, headers);
                }
                if (isBlocked(body)) {
                    log.warn("东财请求被反爬, attempt={}, proxy={}", attempt + 1, proxyLabel(proxy));
                    continue;
                }
                return body;
            } catch (Exception e) {
                lastException = e;
                log.warn("东财请求失败, attempt={}, proxy={}, error={}", attempt + 1, proxyLabel(proxy), e.getMessage());
                humanDelay(500, 1500);
            }
        }
        throw new Exception("东财请求" + maxRetry + "次全部失败: " + lastException);
    }

    // ========== JSONP 解析 ==========

    private JsonNode parseJsonp(String body) {
        if (body == null || body.isBlank()) {
            return objectMapper.createObjectNode();
        }
        String trimmed = body.trim();
        Matcher matcher = JSONP.matcher(trimmed);
        try {
            if (matcher.matches()) {
                return objectMapper.readTree(matcher.group(2));
            }
            return objectMapper.readTree(trimmed);
        } catch (Exception e) {
            log.warn("JSONP解析失败: {}", e.getMessage());
            return objectMapper.createObjectNode();
        }
    }

    private boolean isBlocked(String body) {
        if (body == null) return false;
        String lower = body.toLowerCase();
        return lower.contains("access denied") || lower.contains("403") || lower.contains("频繁")
                || lower.contains("too many") || lower.contains("验证码");
    }

    private String selectProxy() {
        List<String> proxies = proxyProvider.availableProxies();
        if (proxies.isEmpty()) {
            return null;
        }
        return proxies.get(ThreadLocalRandom.current().nextInt(proxies.size()));
    }

    private String proxyLabel(String proxy) {
        return proxy == null || proxy.isBlank() ? "DIRECT" : proxy.substring(0, Math.min(proxy.length(), 20)) + "...";
    }

    private Map<String, String> eastMoneyHeaders() {
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("User-Agent", randomUserAgent());
        headers.put("Accept", "application/json,text/javascript,*/*;q=0.8");
        headers.put("Referer", "http://quote.eastmoney.com/");
        return headers;
    }

    private static final List<String> USER_AGENTS = Arrays.asList(
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36",
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36",
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:132.0) Gecko/20100101 Firefox/132.0",
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/18.2 Safari/605.1.15"
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

    public record EastMoneyPageResult(int totalCount, List<JsonNode> rows) {}
    public record EastMoneyPoolResult(int totalCount, List<JsonNode> rows) {}
}
