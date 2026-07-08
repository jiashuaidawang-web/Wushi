package com.wushi.module.spider.provider.plate;

import com.wushi.module.market.domain.row.StockPlateDimensionRow;
import com.wushi.module.spider.core.SpiderFetchRequest;
import com.wushi.module.spider.core.SpiderResult;
import com.wushi.module.spider.eastmoney.EastMoneyEndpoint;
import com.wushi.module.spider.eastmoney.EastMoneyFieldMapper;
import com.wushi.module.spider.eastmoney.EastMoneySpiderClient;
import com.wushi.module.spider.enums.SpiderProviderType;
import com.wushi.module.spider.enums.SpiderTaskStatus;
import com.wushi.module.spider.provider.plate.PlateDimensionProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class EastMoneyPlateDimensionProviderImpl implements PlateDimensionProvider {

    private final EastMoneySpiderClient eastMoneySpiderClient;
    private final EastMoneyFieldMapper fieldMapper;

    @Override
    public SpiderProviderType providerType() { return SpiderProviderType.EAST_MONEY; }

    @Override
    public SpiderResult<StockPlateDimensionRow> fetchPlateDimension(SpiderFetchRequest request) {
        LocalDate tradeDate = request.getTradeDate();
        log.info("开始抓取东财板块维度: tradeDate={}", tradeDate);
        try {
            List<StockPlateDimensionRow> dimensions = new ArrayList<>();
            fetchPlates(EastMoneyEndpoint.REGION_PLATE, "REGION", dimensions);
            fetchPlates(EastMoneyEndpoint.INDUSTRY_PLATE, "INDUSTRY", dimensions);
            fetchPlates(EastMoneyEndpoint.CONCEPT_PLATE, "CONCEPT", dimensions);
            log.info("东财板块维度抓取完成: dimensions={}", dimensions.size());
            return SpiderResult.<StockPlateDimensionRow>builder()
                    .taskCode("plate_dimension").provider(SpiderProviderType.EAST_MONEY.name())
                    .status(SpiderTaskStatus.SUCCESS).rows(dimensions)
                    .fetchedCount(dimensions.size()).successCount(dimensions.size()).build();
        } catch (Exception e) {
            log.error("东财板块维度抓取失败: {}", e.getMessage(), e);
            return SpiderResult.<StockPlateDimensionRow>builder()
                    .taskCode("plate_dimension").provider(SpiderProviderType.EAST_MONEY.name())
                    .status(SpiderTaskStatus.FAILED).errorMessage(e.getMessage()).build();
        }
    }

    private void fetchPlates(EastMoneyEndpoint endpoint, String plateType, List<StockPlateDimensionRow> dimensions) {
        try {
            var result = eastMoneySpiderClient.fetchPaged(endpoint);
            result.rows().forEach(node -> {
                StockPlateDimensionRow dim = fieldMapper.toPlateDimension(node);
                dimensions.add(new StockPlateDimensionRow(dim.plateCode(), dim.plateName(), plateType,
                        dim.parentPlateCode(), dim.status(), dim.source()));
            });
        } catch (Exception e) {
            log.warn("板块类型抓取失败: {} - {}", plateType, e.getMessage());
        }
    }
}
