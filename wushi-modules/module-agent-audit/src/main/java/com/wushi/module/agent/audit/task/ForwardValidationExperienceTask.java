package com.wushi.module.agent.audit.task;

import com.wushi.common.enums.EngineType;
import com.wushi.common.enums.TargetType;
import com.wushi.common.model.ForwardValidationResult;
import com.wushi.module.backtest.engine.ExperienceFactorEngine;
import com.wushi.module.backtest.engine.ForwardValidationEngine;
import com.wushi.module.backtest.model.ExperienceUpdateRequest;
import com.wushi.module.backtest.model.ForwardValidationRequest;
import com.wushi.module.rule.engine.core.EngineRunContext;
import com.wushi.module.rule.engine.core.EngineStepResult;
import com.wushi.module.rule.engine.task.EngineTask;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Date;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class ForwardValidationExperienceTask implements EngineTask {

    private final ForwardValidationEngine forwardValidationEngine;
    private final ExperienceFactorEngine experienceFactorEngine;
    @Qualifier("clickHouseJdbcTemplate")
    private final JdbcTemplate clickHouseJdbcTemplate;

    @Override
    public String stepName() {
        return "FORWARD_VALIDATION_AND_EXPERIENCE";
    }

    @Override
    public int order() {
        return 30;
    }

    @Override
    public List<String> dependsOn() {
        return List.of("ENGINE_JUDGEMENT");
    }

    @Override
    public EngineStepResult execute(EngineRunContext context) {
        List<ForwardValidationRequest> requests = validationRequests(context.getAsOfDate());
        List<ForwardValidationResult> results = forwardValidationEngine.validateBatch(requests);
        experienceFactorEngine.updateExperience(new ExperienceUpdateRequest(
                context.getAsOfDate(),
                context.getRuleVersion(),
                results
        ));
        return EngineStepResult.success(stepName(), results.size());
    }

    private List<ForwardValidationRequest> validationRequests(LocalDate validationDate) {
        String sql = """
                select w.judgement_id, w.watch_id, w.trade_date, w.watch_date, w.engine_type, w.target_type,
                       w.target_code, w.target_name, w.watch_title, w.condition_expression,
                       w.expected_signal, w.risk_signal, w.rule_version
                from next_day_watch_item w
                left join judgement_forward_validation v
                  on v.judgement_id = w.judgement_id
                 and v.validation_date = w.watch_date
                 and v.forward_days = dateDiff('day', w.trade_date, w.watch_date)
                where w.watch_date <= ?
                  and ifNull(v.judgement_id, '') = ''
                order by w.trade_date desc
                limit 200
                """;
        return clickHouseJdbcTemplate.queryForList(sql, Date.valueOf(validationDate)).stream()
                .map(this::toRequest)
                .toList();
    }

    private ForwardValidationRequest toRequest(Map<String, Object> row) {
        LocalDate tradeDate = localDate(row.get("trade_date"));
        LocalDate validationDate = localDate(row.get("watch_date"));
        return new ForwardValidationRequest(
                text(row.get("judgement_id")),
                text(row.get("watch_id")),
                tradeDate,
                validationDate,
                Math.max(1, (int) java.time.temporal.ChronoUnit.DAYS.between(tradeDate, validationDate)),
                enumValue(EngineType.class, text(row.get("engine_type")), EngineType.MARKET_OVERVIEW),
                enumValue(TargetType.class, text(row.get("target_type")), TargetType.MARKET),
                text(row.get("target_code")),
                text(row.get("target_name")),
                text(row.get("watch_title")),
                text(row.get("condition_expression")),
                text(row.get("expected_signal")),
                text(row.get("risk_signal")),
                text(row.get("rule_version"))
        );
    }

    private LocalDate localDate(Object value) {
        if (value instanceof LocalDate localDate) {
            return localDate;
        }
        if (value instanceof Date date) {
            return date.toLocalDate();
        }
        return LocalDate.parse(String.valueOf(value));
    }

    private String text(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private <E extends Enum<E>> E enumValue(Class<E> enumType, String value, E defaultValue) {
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        return Enum.valueOf(enumType, value);
    }
}
