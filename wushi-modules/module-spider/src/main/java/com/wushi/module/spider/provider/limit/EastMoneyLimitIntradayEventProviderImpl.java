package com.wushi.module.spider.provider.limit;

import com.wushi.module.market.domain.row.StockLimitIntradayEventRow;
import com.wushi.module.spider.core.SpiderFetchRequest;
import com.wushi.module.spider.core.SpiderResult;
import com.wushi.module.spider.enums.SpiderProviderType;
import com.wushi.module.spider.enums.SpiderTaskStatus;
import com.wushi.module.spider.provider.limit.LimitIntradayEventProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
public class EastMoneyLimitIntradayEventProviderImpl implements LimitIntradayEventProvider {

    @Override
    public SpiderProviderType providerType() { return SpiderProviderType.EAST_MONEY; }

    @Override
    public SpiderResult<StockLimitIntradayEventRow> fetchLimitIntradayEvents(SpiderFetchRequest request) {
        log.info("东财涨跌停盘中事件暂无接入: tradeDate={}", request.getTradeDate());
        return SpiderResult.<StockLimitIntradayEventRow>builder()
                .taskCode("limit_intraday_event").provider(SpiderProviderType.EAST_MONEY.name())
                .status(SpiderTaskStatus.SKIPPED).rows(List.of()).fetchedCount(0).build();
    }
}
