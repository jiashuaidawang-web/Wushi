package com.wushi.module.review.service.impl;

import com.wushi.common.enums.EngineType;
import com.wushi.common.enums.EvidenceLabelResult;
import com.wushi.common.enums.TargetType;
import com.wushi.common.model.ForwardValidationResult;
import com.wushi.module.backtest.engine.ExperienceFactorEngine;
import com.wushi.module.backtest.model.ExperienceUpdateRequest;
import com.wushi.module.review.model.EvidenceLabelRequest;
import com.wushi.module.review.model.EvidenceLabelResultInfo;
import com.wushi.module.review.service.EvidenceLabelService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.sql.Date;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DefaultEvidenceLabelService implements EvidenceLabelService {

    private final JdbcTemplate jdbcTemplate;
    @Qualifier("clickHouseJdbcTemplate")
    private final JdbcTemplate clickHouseJdbcTemplate;
    private final ExperienceFactorEngine experienceFactorEngine;

    @Override
    public EvidenceLabelResultInfo label(EvidenceLabelRequest request) {
        String labelId = StringUtils.hasText(request.labelId()) ? request.labelId() : "LABEL-" + UUID.randomUUID();
        LocalDate tradeDate = request.tradeDate() == null ? LocalDate.now() : request.tradeDate();
        jdbcTemplate.update("""
                insert into evidence_manual_label
                (label_id, evidence_id, judgement_id, trade_date, label_result, label_reason, reviewer)
                values (?, ?, ?, ?, ?, ?, ?)
                on duplicate key update label_result = values(label_result), label_reason = values(label_reason),
                reviewer = values(reviewer)
                """, labelId, request.evidenceId(), request.judgementId(),
                tradeDate,
                request.labelResult().name(), request.labelReason(),
                StringUtils.hasText(request.reviewer()) ? request.reviewer() : "manual");
        applyLabelToExperience(labelId, tradeDate, request);
        return new EvidenceLabelResultInfo(labelId, request.evidenceId(), request.judgementId(), request.labelResult(), LocalDateTime.now());
    }

    private void applyLabelToExperience(String labelId, LocalDate tradeDate, EvidenceLabelRequest request) {
        Map<String, Object> evidence = findEvidence(request);
        if (evidence.isEmpty()) {
            saveGrowthLog(tradeDate, labelId, "人工标注未找到原始证据，暂不进入经验：" + request.evidenceId());
            return;
        }

        String validationId = "MANUAL-" + labelId;
        String validationResult = validationResult(request.labelResult());
        BigDecimal contributionScore = contributionScore(request.labelResult());
        clickHouseJdbcTemplate.update("""
                insert into evidence_validation_item
                (validation_id, evidence_id, judgement_id, trade_date, validation_date, factor_code,
                 evidence_type, validation_result, contribution_score)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                validationId,
                text(evidence.get("evidence_id")),
                text(evidence.get("judgement_id")),
                date(evidence.get("trade_date"), tradeDate),
                Date.valueOf(tradeDate),
                text(evidence.get("factor_code")),
                text(evidence.get("evidence_type")),
                validationResult,
                contributionScore
        );

        String ruleVersion = text(evidence.get("rule_version"));
        experienceFactorEngine.updateExperience(new ExperienceUpdateRequest(
                tradeDate,
                StringUtils.hasText(ruleVersion) ? ruleVersion : "v1",
                List.of(manualValidationResult(validationId, tradeDate, evidence, request.labelResult(), contributionScore))
        ));
        saveGrowthLog(tradeDate, labelId, "人工证据标注进入经验成长："
                + request.evidenceId() + "=" + request.labelResult()
                + "，贡献分=" + contributionScore + "，原因=" + request.labelReason());
    }

    private Map<String, Object> findEvidence(EvidenceLabelRequest request) {
        List<Map<String, Object>> rows = clickHouseJdbcTemplate.queryForList("""
                select judgement_id, trade_date, engine_type, target_type, target_code,
                       evidence_id, evidence_type, factor_code, rule_version
                from judgement_evidence_item
                where judgement_id = ? and evidence_id = ?
                order by created_at desc
                limit 1
                """, request.judgementId(), request.evidenceId());
        return rows.isEmpty() ? Map.of() : rows.getFirst();
    }

    private ForwardValidationResult manualValidationResult(String validationId, LocalDate statDate,
                                                           Map<String, Object> evidence,
                                                           EvidenceLabelResult labelResult,
                                                           BigDecimal contributionScore) {
        return ForwardValidationResult.builder()
                .validationId(validationId)
                .judgementId(text(evidence.get("judgement_id")))
                .tradeDate(localDate(evidence.get("trade_date"), statDate))
                .validationDate(statDate)
                .forwardDays(0)
                .engineType(enumValue(EngineType.class, text(evidence.get("engine_type")), EngineType.EXPERIENCE))
                .targetType(enumValue(TargetType.class, text(evidence.get("target_type")), TargetType.MARKET))
                .targetCode(text(evidence.get("target_code")))
                .validationResult(labelResult == EvidenceLabelResult.INVALID ? com.wushi.common.enums.ValidationResultType.MISS : com.wushi.common.enums.ValidationResultType.HIT)
                .realizedSignal("人工证据标注：" + labelResult)
                .returnPct(BigDecimal.ZERO)
                .maxDrawdownPct(BigDecimal.ZERO)
                .scoreDelta(contributionScore)
                .build();
    }

    private String validationResult(EvidenceLabelResult labelResult) {
        return switch (labelResult) {
            case VALID -> "VALID";
            case INVALID -> "INVALID";
            case OVER_WEIGHTED -> "VALID";
            case UNDER_WEIGHTED -> "VALID";
        };
    }

    private BigDecimal contributionScore(EvidenceLabelResult labelResult) {
        return switch (labelResult) {
            case VALID -> BigDecimal.ONE;
            case INVALID -> BigDecimal.ONE.negate();
            case OVER_WEIGHTED -> new BigDecimal("-0.5000");
            case UNDER_WEIGHTED -> new BigDecimal("0.8000");
        };
    }

    private void saveGrowthLog(LocalDate tradeDate, String labelId, String content) {
        jdbcTemplate.update("""
                insert into system_growth_log
                (growth_id, trade_date, growth_type, engine_type, title, content, before_value, after_value, source_ref)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, "GROWTH-" + UUID.randomUUID(), tradeDate, "MANUAL", EngineType.EXPERIENCE.name(),
                "人工标注反哺", content, null, null, labelId);
    }

    private Date date(Object value, LocalDate defaultDate) {
        return Date.valueOf(localDate(value, defaultDate));
    }

    private LocalDate localDate(Object value, LocalDate defaultDate) {
        if (value instanceof LocalDate localDate) {
            return localDate;
        }
        if (value instanceof Date date) {
            return date.toLocalDate();
        }
        if (value == null) {
            return defaultDate;
        }
        return LocalDate.parse(String.valueOf(value));
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private <E extends Enum<E>> E enumValue(Class<E> enumType, String value, E defaultValue) {
        if (!StringUtils.hasText(value)) {
            return defaultValue;
        }
        return Enum.valueOf(enumType, value);
    }
}
