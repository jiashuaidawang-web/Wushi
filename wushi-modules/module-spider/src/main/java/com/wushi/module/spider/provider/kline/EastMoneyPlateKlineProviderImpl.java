package com.wushi.module.spider.provider.kline;

import com.fasterxml.jackson.databind.JsonNode;
import com.wushi.module.market.domain.row.StockPlateDailyKlineRow;
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
public class EastMoneyPlateKlineProviderImpl implements PlateKlineProvider {

    private final EastMoneyPlaywrightClient playwrightClient;
    private final EastMoneyFieldMapper fieldMapper;

    @Override
    public SpiderProviderType providerType() { return SpiderProviderType.EAST_MONEY; }

    @Override
    public SpiderResult<StockPlateDailyKlineRow> fetchPlateDailyKline(SpiderFetchRequest request) {
        LocalDate tradeDate = request.getTradeDate();
        log.info("开始抓取东财板块日K: tradeDate={}", tradeDate);
        List<StockPlateDailyKlineRow> allRows = new ArrayList<>();
        int totalFetched = 0;

        List<EastMoneyEndpoint> plateEndpoints = List.of(
                EastMoneyEndpoint.REGION_PLATE,
                EastMoneyEndpoint.INDUSTRY_PLATE,
                EastMoneyEndpoint.CONCEPT_PLATE
        );
        List<String> plateTypes = List.of("REGION", "INDUSTRY", "CONCEPT");

        try {
            for (int i = 0; i < plateEndpoints.size(); i++) {
                List<JsonNode> rawRows = new ArrayList<>();
                int total = playwrightClient.fetchAllPages(plateEndpoints.get(i), rawRows, "plate_daily_" + plateTypes.get(i));
                String plateType = plateTypes.get(i);
                List<StockPlateDailyKlineRow> rows = rawRows.stream()
                        .map(node -> {
                            StockPlateDailyKlineRow raw = fieldMapper.toPlateDailyKline(tradeDate, node);
                            return new StockPlateDailyKlineRow(
                                    raw.tradeDate(), raw.plateCode(), raw.plateName(), plateType,
                                    raw.open(), raw.high(), raw.low(), raw.close(), raw.preClose(),
                                    raw.changePct(), raw.volume(), raw.amount(), raw.turnoverRate(),
                                    raw.mainNetInflow(), raw.source());
                        })
                        .filter(row -> row.plateCode() != null && !row.plateCode().isBlank())
                        .toList();
                allRows.addAll(rows);
                totalFetched += total;
                log.info("东财板块日K[{}]: total={}, mapped={}", plateType, total, rows.size());
            }
            log.info("东财板块日K抓取完成: total={}, mapped={}", totalFetched, allRows.size());
            return SpiderResult.<StockPlateDailyKlineRow>builder()
                    .taskCode("plate_daily_kline").provider(SpiderProviderType.EAST_MONEY.name())
                    .status(SpiderTaskStatus.SUCCESS).rows(allRows)
                    .fetchedCount(totalFetched).successCount(allRows.size()).build();
        } catch (Exception e) {
            log.error("东财板块日K抓取失败: {}", e.getMessage(), e);
            return SpiderResult.<StockPlateDailyKlineRow>builder()
                    .taskCode("plate_daily_kline").provider(SpiderProviderType.EAST_MONEY.name())
                    .status(SpiderTaskStatus.FAILED).errorMessage(e.getMessage()).build();
        }
    }
}
