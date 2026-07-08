package com.wushi.module.spider.provider.plate;

import com.wushi.module.market.domain.row.StockPlateRelationSnapshotRow;
import com.wushi.module.spider.core.SpiderFetchRequest;
import com.wushi.module.spider.core.SpiderResult;
import com.wushi.module.spider.eastmoney.EastMoneyEndpoint;
import com.wushi.module.spider.eastmoney.EastMoneyFieldMapper;
import com.wushi.module.spider.eastmoney.EastMoneySpiderClient;
import com.wushi.module.spider.enums.SpiderProviderType;
import com.wushi.module.spider.enums.SpiderTaskStatus;
import com.wushi.module.spider.provider.plate.StockPlateRelationProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class EastMoneyStockPlateRelationProviderImpl implements StockPlateRelationProvider {

    private final EastMoneySpiderClient eastMoneySpiderClient;
    private final EastMoneyFieldMapper fieldMapper;

    @Override
    public SpiderProviderType providerType() { return SpiderProviderType.EAST_MONEY; }

    @Override
    public SpiderResult<StockPlateRelationSnapshotRow> fetchStockPlateRelations(SpiderFetchRequest request) {
        LocalDate tradeDate = request.getTradeDate();
        log.info("开始抓取东财板块个股关系: tradeDate={}", tradeDate);
        try {
            List<StockPlateRelationSnapshotRow> relations = new ArrayList<>();
            fetchRelations(EastMoneyEndpoint.REGION_PLATE, "REGION", tradeDate, relations);
            fetchRelations(EastMoneyEndpoint.INDUSTRY_PLATE, "INDUSTRY", tradeDate, relations);
            fetchRelations(EastMoneyEndpoint.CONCEPT_PLATE, "CONCEPT", tradeDate, relations);
            log.info("东财板块个股关系抓取完成: count={}", relations.size());
            return SpiderResult.<StockPlateRelationSnapshotRow>builder()
                    .taskCode("stock_plate_relation").provider(SpiderProviderType.EAST_MONEY.name())
                    .status(SpiderTaskStatus.SUCCESS).rows(relations)
                    .fetchedCount(relations.size()).successCount(relations.size()).build();
        } catch (Exception e) {
            log.error("东财板块个股关系抓取失败: {}", e.getMessage(), e);
            return SpiderResult.<StockPlateRelationSnapshotRow>builder()
                    .taskCode("stock_plate_relation").provider(SpiderProviderType.EAST_MONEY.name())
                    .status(SpiderTaskStatus.FAILED).errorMessage(e.getMessage()).build();
        }
    }

    private void fetchRelations(EastMoneyEndpoint endpoint, String plateType, LocalDate tradeDate,
                                 List<StockPlateRelationSnapshotRow> relations) {
        try {
            var plateResult = eastMoneySpiderClient.fetchPaged(endpoint);
            for (var plateNode : plateResult.rows()) {
                String plateCode = plateNode.path("f12").asText("");
                String plateName = plateNode.path("f14").asText("");
                if (plateCode.isBlank()) continue;
                var stocksResult = eastMoneySpiderClient.fetchPlateStocks(plateCode);
                stocksResult.rows().forEach(stockNode ->
                        relations.add(fieldMapper.toPlateRelation(tradeDate, plateCode, plateName, plateType, stockNode)));
            }
        } catch (Exception e) {
            log.warn("板块关系抓取失败: plateType={}, error={}", plateType, e.getMessage());
        }
    }
}
