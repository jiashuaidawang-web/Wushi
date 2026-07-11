package com.wushi.module.spider.provider.limit;

import com.fasterxml.jackson.databind.JsonNode;
import com.wushi.module.market.domain.row.StockLimitStatusDailyRow;
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
public class EastMoneyLimitStatusProviderImpl implements LimitStatusProvider {

    private final EastMoneyPlaywrightClient playwrightClient;
    private final EastMoneyFieldMapper fieldMapper;

    @Override
    public SpiderProviderType providerType() { return SpiderProviderType.EAST_MONEY; }

    @Override
    public SpiderResult<StockLimitStatusDailyRow> fetchLimitStatus(SpiderFetchRequest request) {
        LocalDate tradeDate = request.getTradeDate();
        log.info("开始抓取东财涨跌停状态: tradeDate={}", tradeDate);
        try {
            List<JsonNode> rawRows = new ArrayList<>();
            playwrightClient.fetchPool(EastMoneyEndpoint.LIMIT_UP_POOL, rawRows, "limit_status", tradeDate);
            List<StockLimitStatusDailyRow> rows = rawRows.stream()
                    .map(node -> fieldMapper.toLimitStatusDaily(tradeDate, node))
                    .filter(row -> row.stockCode() != null && !row.stockCode().isBlank())
                    .toList();
            log.info("东财涨跌停状态抓取完成: count={}", rows.size());
            return SpiderResult.<StockLimitStatusDailyRow>builder()
                    .taskCode("stock_limit_status").provider(SpiderProviderType.EAST_MONEY.name())
                    .status(SpiderTaskStatus.SUCCESS).rows(rows)
                    .fetchedCount(rows.size()).successCount(rows.size()).build();
        } catch (Exception e) {
            log.error("东财涨跌停状态抓取失败: {}", e.getMessage(), e);
            return SpiderResult.<StockLimitStatusDailyRow>builder()
                    .taskCode("stock_limit_status").provider(SpiderProviderType.EAST_MONEY.name())
                    .status(SpiderTaskStatus.FAILED).errorMessage(e.getMessage()).build();
        }
    }
}
