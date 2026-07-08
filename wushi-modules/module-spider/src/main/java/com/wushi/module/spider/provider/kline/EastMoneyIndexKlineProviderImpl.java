package com.wushi.module.spider.provider.kline;

import com.wushi.module.market.domain.row.IndexDailyKlineRow;
import com.wushi.module.spider.core.SpiderFetchRequest;
import com.wushi.module.spider.core.SpiderResult;
import com.wushi.module.spider.enums.SpiderProviderType;
import com.wushi.module.spider.enums.SpiderTaskStatus;
import com.wushi.module.spider.provider.kline.IndexKlineProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
public class EastMoneyIndexKlineProviderImpl implements IndexKlineProvider {

    @Override
    public SpiderProviderType providerType() { return SpiderProviderType.EAST_MONEY; }

    @Override
    public SpiderResult<IndexDailyKlineRow> fetchIndexDailyKline(SpiderFetchRequest request) {
        log.info("东财指数日K暂无接入: tradeDate={}", request.getTradeDate());
        return SpiderResult.<IndexDailyKlineRow>builder()
                .taskCode("index_daily_kline").provider(SpiderProviderType.EAST_MONEY.name())
                .status(SpiderTaskStatus.SKIPPED).rows(List.of()).fetchedCount(0).build();
    }
}
