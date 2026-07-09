package com.wushi.module.spider.provider.kline;

import com.wushi.module.market.domain.row.IndexDailyKlineRow;
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
public class EastMoneyIndexKlineProviderImpl implements IndexKlineProvider {

    private final EastMoneySpiderClient eastMoneySpiderClient;
    private final EastMoneyFieldMapper fieldMapper;

    @Override
    public SpiderProviderType providerType() { return SpiderProviderType.EAST_MONEY; }

    @Override
    public SpiderResult<IndexDailyKlineRow> fetchIndexDailyKline(SpiderFetchRequest request) {
        LocalDate tradeDate = request.getTradeDate();
        log.info("开始抓取东财指数日K: tradeDate={}", tradeDate);
        try {
            var result = eastMoneySpiderClient.fetchPaged(EastMoneyEndpoint.INDEX_KLINE);
            List<IndexDailyKlineRow> rows = result.rows().stream()
                    .map(node -> fieldMapper.toIndexDailyKline(tradeDate, node))
                    .filter(row -> row.indexCode() != null && !row.indexCode().isBlank())
                    .toList();
            log.info("东财指数日K抓取完成: total={}, mapped={}", result.totalCount(), rows.size());
            return SpiderResult.<IndexDailyKlineRow>builder()
                    .taskCode("index_daily_kline").provider(SpiderProviderType.EAST_MONEY.name())
                    .status(SpiderTaskStatus.SUCCESS).rows(rows)
                    .fetchedCount(result.totalCount()).successCount(rows.size()).build();
        } catch (Exception e) {
            log.error("东财指数日K抓取失败: {}", e.getMessage(), e);
            return SpiderResult.<IndexDailyKlineRow>builder()
                    .taskCode("index_daily_kline").provider(SpiderProviderType.EAST_MONEY.name())
                    .status(SpiderTaskStatus.FAILED).errorMessage(e.getMessage()).build();
        }
    }
}
