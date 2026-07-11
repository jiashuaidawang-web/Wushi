package com.wushi.module.spider.provider.kline;

import com.fasterxml.jackson.databind.JsonNode;
import com.wushi.module.market.domain.row.IndexDailyKlineRow;
import com.wushi.module.spider.core.SpiderFetchRequest;
import com.wushi.module.spider.core.SpiderResult;
import com.wushi.module.spider.eastmoney.EastMoneyEndpoint;
import com.wushi.module.spider.eastmoney.EastMoneyFieldMapper;
import com.wushi.module.spider.eastmoney.EastMoneyPlaywrightClient;
import com.wushi.module.spider.enums.SpiderProviderType;
import com.wushi.module.spider.enums.SpiderTaskStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class EastMoneyIndexKlineProviderImpl implements IndexKlineProvider {

    private final EastMoneyPlaywrightClient playwrightClient;
    private final EastMoneyFieldMapper fieldMapper;

    @Override
    public SpiderProviderType providerType() { return SpiderProviderType.EAST_MONEY; }

    @Override
    public SpiderResult<IndexDailyKlineRow> fetchIndexDailyKline(SpiderFetchRequest request) {
        LocalDate tradeDate = request.getTradeDate();
        log.info("开始抓取东财指数日K: tradeDate={}", tradeDate);
        try {
            List<JsonNode> rawRows = new ArrayList<>();
            int total = playwrightClient.fetchAllPages(EastMoneyEndpoint.INDEX_KLINE, rawRows, "index_daily_kline");
            List<IndexDailyKlineRow> rows = rawRows.stream()
                    .map(node -> fieldMapper.toIndexDailyKline(tradeDate, node))
                    .filter(row -> row.indexCode() != null && !row.indexCode().isBlank())
                    .toList();
            log.info("东财指数日K抓取完成: total={}, mapped={}", total, rows.size());
            if (total == 0) {
                return SpiderResult.<IndexDailyKlineRow>builder()
                        .taskCode("index_daily_kline").provider(SpiderProviderType.EAST_MONEY.name())
                        .status(SpiderTaskStatus.FAILED).errorMessage("Playwright 抓取返回 0 条").build();
            }
            return SpiderResult.<IndexDailyKlineRow>builder()
                    .taskCode("index_daily_kline").provider(SpiderProviderType.EAST_MONEY.name())
                    .status(SpiderTaskStatus.SUCCESS).rows(rows)
                    .fetchedCount(total).successCount(rows.size()).build();
        } catch (Exception e) {
            log.error("东财指数日K抓取失败: {}", e.getMessage(), e);
            return SpiderResult.<IndexDailyKlineRow>builder()
                    .taskCode("index_daily_kline").provider(SpiderProviderType.EAST_MONEY.name())
                    .status(SpiderTaskStatus.FAILED).errorMessage(e.getMessage()).build();
        }
    }
}
