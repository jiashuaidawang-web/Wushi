package com.wushi.module.review.service.impl;

import com.wushi.common.enums.CorrectionStatus;
import com.wushi.common.enums.JudgementMode;
import com.wushi.common.enums.ValidationResultType;
import com.wushi.common.model.ForwardValidationResult;
import com.wushi.module.backtest.engine.ExperienceFactorEngine;
import com.wushi.module.backtest.model.ExperienceUpdateRequest;
import com.wushi.module.review.model.CorrectionFieldItem;
import com.wushi.module.review.model.ManualCorrectionRequest;
import com.wushi.module.review.model.ManualCorrectionResult;
import com.wushi.module.review.service.ManualCorrectionService;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DefaultManualCorrectionService implements ManualCorrectionService {

    private final JdbcTemplate jdbcTemplate;
    private final ExperienceFactorEngine experienceFactorEngine;

    @Override
    public ManualCorrectionResult correct(ManualCorrectionRequest request) {
        String correctionId = StringUtils.hasText(request.correctionId()) ? request.correctionId() : "CORR-" + UUID.randomUUID();
        LocalDate tradeDate = request.tradeDate() == null ? LocalDate.now() : request.tradeDate();
        LocalDate asOfDate = request.asOfDate() == null ? tradeDate : request.asOfDate();
        JudgementMode mode = request.judgementMode() == null ? JudgementMode.RETROSPECTIVE : request.judgementMode();
        String sql = """
                insert into manual_correction_record
                (correction_id, trade_date, as_of_date, judgement_mode, engine_type, target_type, target_code,
                 judgement_id, correction_type, correction_reason, reviewer, status)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                on duplicate key update correction_reason = values(correction_reason), reviewer = values(reviewer),
                status = values(status), updated_at = current_timestamp
                """;
        jdbcTemplate.update(sql, correctionId, tradeDate, asOfDate, mode.name(),
                request.engineType().name(), request.targetType().name(), safe(request.targetCode(), "MARKET"),
                request.judgementId(), request.correctionType().name(), request.correctionReason(),
                safe(request.reviewer(), "manual"), CorrectionStatus.EFFECTIVE.name());
        List<CorrectionFieldItem> items = request.items() == null ? List.of() : request.items();
        jdbcTemplate.update("delete from manual_correction_item where correction_id = ?", correctionId);
        for (CorrectionFieldItem item : items) {
            jdbcTemplate.update("""
                    insert into manual_correction_item
                    (correction_id, field_name, old_value, new_value, field_desc)
                    values (?, ?, ?, ?, ?)
                    """, correctionId, item.fieldName(), item.oldValue(), item.newValue(), item.fieldDesc());
        }
        applyCorrectionToExperience(correctionId, tradeDate, asOfDate, request);
        saveGrowthLog(tradeDate, correctionId, "人工修正 " + request.engineType() + "/" + request.targetCode() + "：" + request.correctionReason());
        return new ManualCorrectionResult(correctionId, CorrectionStatus.EFFECTIVE, items.size(), LocalDateTime.now());
    }

    @Override
    public ManualCorrectionResult revoke(String correctionId, String reviewer, String reason) {
        jdbcTemplate.update("""
                update manual_correction_record
                set status = ?, correction_reason = concat(correction_reason, '\n撤销：', ?), reviewer = ?, updated_at = current_timestamp
                where correction_id = ?
                """, CorrectionStatus.REVOKED.name(), safe(reason, ""), safe(reviewer, "manual"), correctionId);
        return new ManualCorrectionResult(correctionId, CorrectionStatus.REVOKED, 0, LocalDateTime.now());
    }

    private void applyCorrectionToExperience(String correctionId, LocalDate tradeDate, LocalDate asOfDate, ManualCorrectionRequest request) {
        if (request.engineType() == null || request.targetType() == null) {
            return;
        }
        BigDecimal scoreDelta = correctionScoreDelta(request.correctionType());
        ForwardValidationResult manualResult = ForwardValidationResult.builder()
                .validationId("MANUAL-" + correctionId)
                .judgementId(safe(request.judgementId(), correctionId))
                .tradeDate(tradeDate)
                .validationDate(asOfDate)
                .forwardDays(0)
                .engineType(request.engineType())
                .targetType(request.targetType())
                .targetCode(safe(request.targetCode(), "MARKET"))
                .validationResult(scoreDelta.signum() < 0 ? ValidationResultType.MISS : ValidationResultType.HIT)
                .realizedSignal("人工修正：" + request.correctionType() + "，" + request.correctionReason())
                .returnPct(BigDecimal.ZERO)
                .maxDrawdownPct(BigDecimal.ZERO)
                .scoreDelta(scoreDelta)
                .build();
        experienceFactorEngine.updateExperience(new ExperienceUpdateRequest(
                asOfDate,
                "MANUAL",
                List.of(manualResult)
        ));
    }

    private BigDecimal correctionScoreDelta(com.wushi.common.enums.CorrectionType correctionType) {
        if (correctionType == com.wushi.common.enums.CorrectionType.SAMPLE) {
            return new BigDecimal("0.5000");
        }
        return BigDecimal.ONE.negate();
    }

    private void saveGrowthLog(LocalDate tradeDate, String correctionId, String content) {
        jdbcTemplate.update("""
                insert into system_growth_log
                (growth_id, trade_date, growth_type, engine_type, title, content, before_value, after_value, source_ref)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, "GROWTH-" + UUID.randomUUID(), tradeDate, "MANUAL", null,
                "人工修正反哺", content, null, null, correctionId);
    }

    private String safe(String value, String defaultValue) {
        return StringUtils.hasText(value) ? value : defaultValue;
    }
}
