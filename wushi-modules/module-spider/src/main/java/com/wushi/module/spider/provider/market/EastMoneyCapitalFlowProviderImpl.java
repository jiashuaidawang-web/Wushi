package com.wushi.module.spider.provider.market;

import com.wushi.module.market.domain.row.CapitalFlowDailySnapshotRow;
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
public class EastMoneyCapitalFlowProviderImpl implements CapitalFlowProvider {

    private final EastMoneySpiderClient eastMoneySpiderClient;
    private final EastMoneyFieldMapper fieldMapper;

    @Override
    public SpiderProviderType providerType() { return SpiderProviderType.EAST_MONEY; }

    @Override
    public SpiderResult<CapitalFlowDailySnapshotRow> fetchCapitalFlow(SpiderFetchRequest request) {
        LocalDate tradeDate = request.getTradeDate();
        log.info("开始抓取东财资金流向: tradeDate={}", tradeDate);
        try {
            var result = eastMoneySpiderClient.fetchPaged(EastMoneyEndpoint.CAPITAL_FLOW);
            List<CapitalFlowDailySnapshotRow> rows = result.rows().stream()
                    .map(node -> fieldMapper.toCapitalFlowDailySnapshot(tradeDate, node))
                    .filter(row -> row.targetCode() != null && !row.targetCode().isBlank())
                    .toList();
            log.info("东财资金流向抓取完成: total={}, mapped={}", result.totalCount(), rows.size());
            return SpiderResult.<CapitalFlowDailySnapshotRow>builder()
                    .taskCode("capital_flow").provider(SpiderProviderType.EAST_MONEY.name())
                    .status(SpiderTaskStatus.SUCCESS).rows(rows)
                    .fetchedCount(result.totalCount()).successCount(rows.size()).build();
        } catch (Exception e) {
            log.error("东财资金流向抓取失败: {}", e.getMessage(), e);
            return SpiderResult.<CapitalFlowDailySnapshotRow>builder()
                    .taskCode("capital_flow").provider(SpiderProviderType.EAST_MONEY.name())
                    .status(SpiderTaskStatus.FAILED).errorMessage(e.getMessage()).build();
        }
    }
}
