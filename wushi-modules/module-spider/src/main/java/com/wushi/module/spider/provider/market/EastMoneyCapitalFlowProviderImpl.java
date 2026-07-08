package com.wushi.module.spider.provider.market;

import com.wushi.module.market.domain.row.CapitalFlowDailySnapshotRow;
import com.wushi.module.spider.core.SpiderFetchRequest;
import com.wushi.module.spider.core.SpiderResult;
import com.wushi.module.spider.enums.SpiderProviderType;
import com.wushi.module.spider.enums.SpiderTaskStatus;
import com.wushi.module.spider.provider.market.CapitalFlowProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
public class EastMoneyCapitalFlowProviderImpl implements CapitalFlowProvider {

    @Override
    public SpiderProviderType providerType() { return SpiderProviderType.EAST_MONEY; }

    @Override
    public SpiderResult<CapitalFlowDailySnapshotRow> fetchCapitalFlow(SpiderFetchRequest request) {
        log.info("东财资金流向暂无接入: tradeDate={}", request.getTradeDate());
        return SpiderResult.<CapitalFlowDailySnapshotRow>builder()
                .taskCode("capital_flow").provider(SpiderProviderType.EAST_MONEY.name())
                .status(SpiderTaskStatus.SKIPPED).rows(List.of()).fetchedCount(0).build();
    }
}
