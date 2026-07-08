package com.wushi.module.spider.eastmoney;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wushi.module.spider.common.SpiderHttpClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class EastMoneySpiderClient {

    private static final int PAGE_SIZE = 100;
    private static final DateTimeFormatter BASIC_DATE = DateTimeFormatter.BASIC_ISO_DATE;

    private final SpiderHttpClient httpClient;
    private final ObjectMapper objectMapper;

    /**
     * 分页获取股票列表或板块列表
     */
    public EastMoneyPageResult fetchPaged(EastMoneyEndpoint endpoint) {
        int total = 0;
        List<JsonNode> allRows = new ArrayList<>();
        int totalPage = 1;

        for (int page = 1; page <= totalPage; page++) {
            String url = String.format(endpoint.getUrlTemplate(), page, System.currentTimeMillis());
            try {
                String body = httpClient.get(url, eastMoneyHeaders());
                JsonNode root = parseJsonp(body);
                JsonNode data = root.path("data");
                total = data.path("total").asInt(total);
                totalPage = Math.max(1, (int) Math.ceil(total / (double) PAGE_SIZE));
                if (data.path("diff").isArray()) {
                    data.path("diff").forEach(allRows::add);
                }
                log.debug("Fetched {} page {}/{}, rows={}", endpoint.name(), page, totalPage, data.path("diff").size());
                // 礼貌延迟，避免触发反爬
                sleep(200);
            } catch (Exception e) {
                log.error("东方财富分页接口请求失败: endpoint={}, page={}, error={}", endpoint.name(), page, e.getMessage());
                break;
            }
        }
        return new EastMoneyPageResult(total, allRows);
    }

    /**
     * 获取股票池数据（涨停/跌停/强势等）
     */
    public EastMoneyPoolResult fetchPool(EastMoneyEndpoint endpoint, LocalDate tradeDate) {
        String url = String.format(endpoint.getUrlTemplate(), tradeDate.format(BASIC_DATE), System.currentTimeMillis());
        try {
            String body = httpClient.get(url, eastMoneyHeaders());
            JsonNode root = parseJsonp(body);
            JsonNode data = root.path("data");
            int total = data.path("tc").asInt(0);
            List<JsonNode> rows = new ArrayList<>();
            if (data.path("pool").isArray()) {
                data.path("pool").forEach(rows::add);
            }
            return new EastMoneyPoolResult(total, rows);
        } catch (Exception e) {
            log.error("东方财富股票池接口请求失败: endpoint={}, date={}, error={}", endpoint.name(), tradeDate, e.getMessage());
            return new EastMoneyPoolResult(0, List.of());
        }
    }

    /**
     * 根据板块代码获取板块下个股
     */
    public EastMoneyPageResult fetchPlateStocks(String plateCode) {
        String bkNumber = plateCode == null ? "" : plateCode.replace("BK", "");
        // 使用 push2.eastmoney.com 的板块个股接口
        String template = "https://push2.eastmoney.com/api/qt/clist/get?np=1&fltt=2&invt=2&cb=jQuery37103197788499441794_%d&fs=b:BK%s+f:!50&fields=f12,f13,f14,f1,f2,f4,f3,f152,f5,f6,f7,f15,f18,f16,f17,f10,f8,f9,f23&fid=f3&pn=%d&pz=100&po=1&dect=1&ut=fa5fd1943c7b386f172d6893dbfba10b&_=%d";

        int total = 0;
        List<JsonNode> allRows = new ArrayList<>();
        int totalPage = 1;
        long cb = System.currentTimeMillis();

        for (int page = 1; page <= totalPage; page++) {
            String url = String.format(template, cb, bkNumber, page, System.currentTimeMillis());
            try {
                String body = httpClient.get(url, eastMoneyHeaders());
                JsonNode root = parseJsonp(body);
                JsonNode data = root.path("data");
                if (data.isMissingNode() || data.isNull()) break;
                total = data.path("total").asInt(total);
                totalPage = Math.max(1, (int) Math.ceil(total / (double) PAGE_SIZE));
                if (data.path("diff").isArray()) {
                    data.path("diff").forEach(allRows::add);
                }
                sleep(200);
            } catch (Exception e) {
                log.error("东方财富板块个股请求失败: plateCode={}, page={}, error={}", plateCode, page, e.getMessage());
                break;
            }
        }
        return new EastMoneyPageResult(total, allRows);
    }

    // ========== 工具方法 ==========

    /**
     * 解析 JSONP 响应：剥离回调函数包裹，返回 data 节点
     */
    private JsonNode parseJsonp(String body) {
        if (body == null || body.isBlank()) {
            return objectMapper.createObjectNode();
        }
        String trimmed = body.trim();
        int start = trimmed.indexOf('(');
        int end = trimmed.lastIndexOf(')');
        try {
            if (start >= 0 && end > start) {
                return objectMapper.readTree(trimmed.substring(start + 1, end));
            }
            return objectMapper.readTree(trimmed);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("非法 JSON 内容: " + trimmed, e);
        }
    }

    private Map<String, String> eastMoneyHeaders() {
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36");
        headers.put("Accept", "application/json,text/javascript,*/*;q=0.8");
        headers.put("Referer", "http://quote.eastmoney.com/");
        return headers;
    }

    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public record EastMoneyPageResult(int totalCount, List<JsonNode> rows) {}
    public record EastMoneyPoolResult(int totalCount, List<JsonNode> rows) {}
}
