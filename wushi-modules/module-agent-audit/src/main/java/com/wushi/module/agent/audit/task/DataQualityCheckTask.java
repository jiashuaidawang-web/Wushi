package com.wushi.module.agent.audit.task;

import com.wushi.module.market.enums.FactTable;
import com.wushi.module.market.service.MarketFactService;
import com.wushi.module.rule.engine.core.EngineRunContext;
import com.wushi.module.rule.engine.core.EngineStepResult;
import com.wushi.module.rule.engine.task.EngineTask;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class DataQualityCheckTask implements EngineTask {

    private final MarketFactService marketFactService;

    @Override
    public String stepName() {
        return "DATA_QUALITY_CHECK";
    }

    @Override
    public int order() {
        return 10;
    }

    @Override
    public EngineStepResult execute(EngineRunContext context) {
        List<FactTable> required = List.of(
                FactTable.MARKET_BREADTH_DAILY_SNAPSHOT,
                FactTable.STOCK_LIMIT_STATUS_DAILY,
                FactTable.PLATE_DAILY_SNAPSHOT
        );
        long rows = 0;
        for (FactTable table : required) {
            rows += marketFactService.findByTradeDate(table, context.getTradeDate()).size();
        }
        if (rows == 0) {
            return EngineStepResult.builder()
                    .stepName(stepName())
                    .success(false)
                    .affectedRows(0)
                    .message("核心事实表无数据，停止当日推演")
                    .shouldContinue(false)
                    .build();
        }
        return EngineStepResult.success(stepName(), rows);
    }
}
