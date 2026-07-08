package com.wushi.module.spider.provider.kline;

import com.wushi.module.market.domain.row.StockPlateDailyKlineRow;
import com.wushi.module.spider.core.SpiderFetchRequest;
import com.wushi.module.spider.core.SpiderResult;
import com.wushi.module.spider.enums.SpiderProviderType;
import com.wushi.module.spider.enums.SpiderTaskStatus;
import com.wushi.module.spider.provider.kline.PlateKlineProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
public class EastMoneyPlateKlineProviderImpl implements PlateKlineProvider {

    @Override
    public SpiderProviderType providerType() { return SpiderProviderType.EAST_MONEY; }

    @Override
    public SpiderResult<StockPlateDailyKlineRow> fetchPlateDailyKline(SpiderFetchRequest request) {
        log.info("东财板块日K暂无接入: tradeDate={}", request.getTradeDate());
        return SpiderResult.<StockPlateDailyKlineRow>builder()
                .taskCode("plate_daily_kline").provider(SpiderProviderType.EAST_MONEY.name())
                .status(SpiderTaskStatus.SKIPPED).rows(List.of()).fetchedCount(0).build();
    }
}
