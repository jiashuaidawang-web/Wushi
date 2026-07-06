package com.wushi.module.agent.audit.task;

import com.wushi.common.model.EvidenceItem;
import com.wushi.common.model.JudgementResult;
import com.wushi.common.model.NextWatchItem;
import com.wushi.module.agent.audit.inference.DailyInferencePipeline;
import com.wushi.module.agent.audit.inference.MarketInferenceContext;
import com.wushi.module.rule.engine.core.EngineRunContext;
import com.wushi.module.rule.engine.core.EngineStepResult;
import com.wushi.module.rule.engine.task.EngineTask;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Date;
import java.util.List;

@Component
@RequiredArgsConstructor
public class DailyJudgementTask implements EngineTask {

    private final DailyInferencePipeline dailyInferencePipeline;
    @Qualifier("clickHouseJdbcTemplate")
    private final JdbcTemplate clickHouseJdbcTemplate;

    @Override
    public String stepName() {
        return "ENGINE_JUDGEMENT";
    }

    @Override
    public int order() {
        return 20;
    }

    @Override
    public List<String> dependsOn() {
        return List.of("DATA_QUALITY_CHECK");
    }

    @Override
    public EngineStepResult execute(EngineRunContext context) {
        MarketInferenceContext inferenceContext = dailyInferencePipeline.infer(context);
        List<JudgementResult<?>> results = inferenceContext.allResults();
        long affected = 0;
        for (JudgementResult<?> result : results) {
            affected += saveEvidence(result, result.getEvidenceList(), "SUPPORT");
            affected += saveEvidence(result, result.getConflictList(), "CONFLICT");
            affected += saveEvidence(result, result.getWarningList(), "WARNING");
            affected += saveNextWatch(result);
        }
        return EngineStepResult.success(stepName(), affected);
    }

    private int saveEvidence(JudgementResult<?> result, List<EvidenceItem> items, String evidenceType) {
        if (items == null || items.isEmpty()) {
            return 0;
        }
        String sql = """
                insert into judgement_evidence_item
                (judgement_id, trade_date, as_of_date, judgement_mode, engine_type, target_type, target_code,
                 evidence_id, evidence_type, factor_code, factor_name, evidence_title, evidence_desc,
                 factor_value, score, weight, source_table, source_key, rule_version)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
        int count = 0;
        for (EvidenceItem item : items) {
            clickHouseJdbcTemplate.update(sql,
                    result.getJudgementId(),
                    Date.valueOf(result.getTradeDate()),
                    Date.valueOf(result.getAsOfDate()),
                    result.getJudgementMode().name(),
                    result.getEngineType().name(),
                    result.getTargetType().name(),
                    result.getTargetCode() == null ? "" : result.getTargetCode(),
                    item.getEvidenceCode(),
                    evidenceType,
                    item.getEvidenceCode(),
                    item.getTitle(),
                    item.getTitle(),
                    item.getDescription(),
                    item.getScore(),
                    item.getScore(),
                    item.getWeight(),
                    item.getSourceTable(),
                    item.getSourceKey(),
                    result.getRuleVersion());
            count++;
        }
        return count;
    }

    private int saveNextWatch(JudgementResult<?> result) {
        List<NextWatchItem> items = result.getNextWatchList();
        if (items == null || items.isEmpty()) {
            return 0;
        }
        String sql = """
                insert into next_day_watch_item
                (watch_id, judgement_id, trade_date, watch_date, as_of_date, judgement_mode, engine_type,
                 target_type, target_code, target_name, watch_title, condition_expression, expected_signal,
                 risk_signal, priority, rule_version)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
        int count = 0;
        for (NextWatchItem item : items) {
            clickHouseJdbcTemplate.update(sql,
                    item.getWatchId(),
                    result.getJudgementId(),
                    Date.valueOf(item.getTradeDate()),
                    Date.valueOf(item.getWatchDate()),
                    Date.valueOf(result.getAsOfDate()),
                    result.getJudgementMode().name(),
                    item.getEngineType().name(),
                    item.getTargetType().name(),
                    item.getTargetCode() == null ? "" : item.getTargetCode(),
                    item.getTargetName(),
                    item.getTitle(),
                    item.getConditionExpression(),
                    item.getExpectedSignal(),
                    item.getRiskSignal(),
                    item.getPriority(),
                    item.getRuleVersion());
            count++;
        }
        return count;
    }
}
