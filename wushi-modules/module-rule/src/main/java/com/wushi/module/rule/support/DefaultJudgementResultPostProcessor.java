package com.wushi.module.rule.support;

import com.wushi.common.enums.EvidenceType;
import com.wushi.common.model.DataQualityContext;
import com.wushi.common.model.EvidenceItem;
import com.wushi.common.model.JudgementResult;
import com.wushi.common.model.RuleContext;
import com.wushi.module.rule.engine.core.EngineRequest;
import com.wushi.module.rule.quality.ConfidenceAdjuster;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class DefaultJudgementResultPostProcessor implements JudgementResultPostProcessor {

    private static final BigDecimal DEFAULT_CONFIDENCE = new BigDecimal("0.5000");

    private final ConfidenceAdjuster confidenceAdjuster;
    private final JdbcTemplate jdbcTemplate;

    @Override
    public <T> JudgementResult<T> apply(EngineRequest request, JudgementResult<T> result) {
        if (result == null) {
            return null;
        }
        RuleContext ruleContext = request == null ? null : request.ruleContext();
        DataQualityContext dataQualityContext = result.getDataQualityContext() != null
                ? result.getDataQualityContext()
                : request == null ? null : request.dataQualityContext();

        String ruleVersion = firstText(result.getRuleVersion(),
                ruleContext == null ? null : ruleContext.getRuleVersion(),
                request == null ? null : request.ruleVersion());

        result.setRuleVersion(ruleVersion);
        result.setDataQualityContext(dataQualityContext);
        if (dataQualityContext != null && result.getDataQualityLevel() == null) {
            result.setDataQualityLevel(dataQualityContext.getLevel());
        }

        BigDecimal baseConfidence = result.getConfidence() == null ? DEFAULT_CONFIDENCE : result.getConfidence();
        result.setConfidence(confidenceAdjuster.applyDataQualityPenalty(baseConfidence, dataQualityContext));
        applyManualCorrection(result);
        return result;
    }

    private <T> void applyManualCorrection(JudgementResult<T> result) {
        if (result.getTradeDate() == null || result.getAsOfDate() == null
                || result.getJudgementMode() == null || result.getEngineType() == null || result.getTargetType() == null) {
            return;
        }
        String targetCode = firstText(result.getTargetCode(), "MARKET");
        List<Map<String, Object>> corrections = jdbcTemplate.queryForList("""
                select correction_id, correction_type, correction_reason, reviewer
                from manual_correction_record
                where trade_date = ?
                  and as_of_date = ?
                  and judgement_mode = ?
                  and engine_type = ?
                  and target_type = ?
                  and target_code = ?
                  and status = 'EFFECTIVE'
                order by updated_at desc
                limit 1
                """,
                result.getTradeDate(),
                result.getAsOfDate(),
                result.getJudgementMode().name(),
                result.getEngineType().name(),
                result.getTargetType().name(),
                targetCode);
        if (corrections.isEmpty()) {
            return;
        }
        Map<String, Object> correction = corrections.get(0);
        String correctionId = String.valueOf(correction.get("correction_id"));
        List<Map<String, Object>> items = jdbcTemplate.queryForList("""
                select field_name, new_value, field_desc
                from manual_correction_item
                where correction_id = ?
                """, correctionId);
        for (Map<String, Object> item : items) {
            String fieldName = String.valueOf(item.get("field_name"));
            String newValue = String.valueOf(item.get("new_value"));
            if ("conclusion".equalsIgnoreCase(fieldName)) {
                result.setConclusion(newValue);
            } else if ("confidence".equalsIgnoreCase(fieldName) && StringUtils.hasText(newValue)) {
                BigDecimal correctedConfidence = parseConfidence(newValue);
                if (correctedConfidence != null) {
                    result.setConfidence(correctedConfidence);
                }
            }
        }
        appendManualCorrectionWarning(result, correctionId, correction, items.size());
    }

    private BigDecimal parseConfidence(String value) {
        try {
            return new BigDecimal(value).max(BigDecimal.ZERO).min(BigDecimal.ONE);
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private <T> void appendManualCorrectionWarning(JudgementResult<T> result, String correctionId,
                                                  Map<String, Object> correction, int itemCount) {
        List<EvidenceItem> warnings = result.getWarningList() == null
                ? new ArrayList<>()
                : new ArrayList<>(result.getWarningList());
        warnings.add(EvidenceItem.builder()
                .evidenceCode("MANUAL_CORRECTION_" + correctionId)
                .evidenceType(EvidenceType.WARNING)
                .title("人工修正已介入")
                .description("修正类型=" + correction.get("correction_type")
                        + "，修正字段数=" + itemCount
                        + "，原因=" + correction.get("correction_reason")
                        + "，修正人=" + correction.get("reviewer"))
                .score(BigDecimal.ZERO)
                .weight(BigDecimal.ZERO)
                .sourceTable("manual_correction_record")
                .sourceKey(correctionId)
                .ruleVersion(result.getRuleVersion())
                .build());
        result.setWarningList(warnings);
    }

    private String firstText(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }
}
