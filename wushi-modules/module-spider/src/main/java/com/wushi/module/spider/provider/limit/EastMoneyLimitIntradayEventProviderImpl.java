package com.wushi.module.spider.provider.limit;

import com.wushi.module.market.domain.row.StockLimitIntradayEventRow;
import com.wushi.module.spider.core.SpiderFetchRequest;
import com.wushi.module.spider.core.SpiderResult;
import com.wushi.module.spider.eastmoney.EastMoneyEndpoint;
import com.wushi.module.spider.eastmoney.EastMoneyFieldMapper;
import com.wushi.module.spider.eastmoney.EastMoneySpiderClient;
import com.wushi.module.spider.enums.SpiderProviderType;
import com.wushi.module.spider.enums.SpiderTaskStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class EastMoneyLimitIntradayEventProviderImpl implements LimitIntradayEventProvider {

    private final EastMoneySpiderClient eastMoneySpiderClient;
    private final EastMoneyFieldMapper fieldMapper;

    @Override
    public SpiderProviderType providerType() { return SpiderProviderType.EAST_MONEY; }

    @Override
    public SpiderResult<StockLimitIntradayEventRow> fetchLimitIntradayEvents(SpiderFetchRequest request) {
        LocalDate tradeDate = request.getTradeDate();
        log.info("开始抓取东财涨停盘中事件: tradeDate={}", tradeDate);
        try {
            var poolResult = eastMoneySpiderClient.fetchPool(EastMoneyEndpoint.LIMIT_UP_POOL, tradeDate);
            List<StockLimitIntradayEventRow> rows = poolResult.rows().stream()
                    .map(node -> fieldMapper.toLimitIntradayEventRow(tradeDate, node))
                    .filter(row -> row.stockCode() != null && !row.stockCode().isBlank())
                    .toList();
            log.info("东财涨停盘中事件抓取完成: count={}", rows.size());
            return SpiderResult.<StockLimitIntradayEventRow>builder()
                    .taskCode("limit_intraday_event").provider(SpiderProviderType.EAST_MONEY.name())
                    .status(SpiderTaskStatus.SUCCESS).rows(rows)
                    .fetchedCount(poolResult.totalCount()).successCount(rows.size()).build();
        } catch (Exception e) {
            log.error("东财涨停盘中事件抓取失败: {}", e.getMessage(), e);
            return SpiderResult.<StockLimitIntradayEventRow>builder()
                    .taskCode("limit_intraday_event").provider(SpiderProviderType.EAST_MONEY.name())
                    .status(SpiderTaskStatus.FAILED).errorMessage(e.getMessage()).build();
        }
    }
}
