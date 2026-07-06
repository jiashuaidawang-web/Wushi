package com.wushi.module.backtest.engine.impl;

import com.wushi.common.enums.EngineType;
import com.wushi.common.enums.TargetType;
import com.wushi.common.enums.ValidationResultType;
import com.wushi.common.model.ForwardValidationResult;
import com.wushi.module.backtest.engine.ForwardValidationEngine;
import com.wushi.module.backtest.model.ForwardValidationRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Date;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DefaultForwardValidationEngine implements ForwardValidationEngine {

    @Qualifier("clickHouseJdbcTemplate")
    private final JdbcTemplate clickHouseJdbcTemplate;

    @Override
    public ForwardValidationResult validate(ForwardValidationRequest request) {
        ForwardValidationResult result = calculate(request);
        save(result);
        return result;
    }

    @Override
    public List<ForwardValidationResult> validateBatch(List<ForwardValidationRequest> requests) {
        if (requests == null || requests.isEmpty()) {
            return List.of();
        }
        return requests.stream().map(this::validate).toList();
    }

    private ForwardValidationResult calculate(ForwardValidationRequest request) {
        if (request.engineType() == EngineType.CYCLE) {
            return validateCycle(request);
        }
        if (request.engineType() == EngineType.MAINLINE) {
            return validateMainline(request);
        }
        if (request.engineType() == EngineType.LEADER) {
            return validateLeader(request);
        }
        if (request.engineType() == EngineType.DIVERGENCE_CONSENSUS) {
            return validateDivergenceConsensus(request);
        }
        if (request.engineType() == EngineType.RISK) {
            return validateRisk(request);
        }
        BigDecimal base = closeOf(request.targetType(), request.targetCode(), request.tradeDate());
        BigDecimal forward = closeOf(request.targetType(), request.targetCode(), request.validationDate());
        if (base.signum() <= 0 || forward.signum() <= 0) {
            return result(request, ValidationResultType.INSUFFICIENT, "验证区间缺少价格/广度事实", BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
        }
        BigDecimal returnPct = forward.subtract(base).multiply(BigDecimal.valueOf(100)).divide(base, 6, RoundingMode.HALF_UP);
        BigDecimal maxDrawdown = maxDrawdown(request.targetType(), request.targetCode(), request.tradeDate(), request.validationDate(), base);
        ValidationResultType type = validationType(request.engineType(), returnPct, maxDrawdown);
        BigDecimal scoreDelta = switch (type) {
            case HIT -> BigDecimal.valueOf(1);
            case CONFLICT_HIT -> BigDecimal.valueOf(0.5);
            case MISS -> BigDecimal.valueOf(-1);
            case INSUFFICIENT -> BigDecimal.ZERO;
        };
        String signal = "T+" + request.forwardDays() + " return=" + returnPct + "%, drawdown=" + maxDrawdown + "%";
        return result(request, type, signal, returnPct, maxDrawdown, scoreDelta);
    }

    private ForwardValidationResult validateCycle(ForwardValidationRequest request) {
        Map<String, Object> base = firstRow("""
                select money_effect_score, loss_effect_score, limit_up_count, limit_down_count, up_count, down_count
                from market_breadth_daily_snapshot
                where trade_date = ?
                limit 1
                """, request.tradeDate());
        Map<String, Object> forward = firstRow("""
                select money_effect_score, loss_effect_score, limit_up_count, limit_down_count, up_count, down_count
                from market_breadth_daily_snapshot
                where trade_date = ?
                limit 1
                """, request.validationDate());
        if (base.isEmpty() || forward.isEmpty()) {
            return insufficient(request, "周期验证缺少市场宽度事实");
        }
        BigDecimal baseLoss = decimal(base.get("loss_effect_score"));
        BigDecimal forwardLoss = decimal(forward.get("loss_effect_score"));
        BigDecimal baseMoney = decimal(base.get("money_effect_score"));
        BigDecimal forwardMoney = decimal(forward.get("money_effect_score"));
        BigDecimal breadthChange = breadthScore(forward).subtract(breadthScore(base));
        boolean hit = forwardLoss.compareTo(baseLoss) <= 0 && (forwardMoney.compareTo(baseMoney) >= 0 || breadthChange.compareTo(new BigDecimal("-0.0500")) >= 0);
        boolean conflictHit = forwardLoss.compareTo(baseLoss) <= 0 || forwardMoney.compareTo(baseMoney) >= 0;
        String signal = watchSignal(request) + "；money " + baseMoney + "->" + forwardMoney
                + ", loss " + baseLoss + "->" + forwardLoss + ", breadthChange=" + breadthChange;
        return validationResult(request, hit, conflictHit, signal, breadthChange.multiply(new BigDecimal("100")), forwardLoss.subtract(baseLoss).negate());
    }

    private ForwardValidationResult validateMainline(ForwardValidationRequest request) {
        Map<String, Object> base = plateRow(request.tradeDate(), request.targetCode());
        Map<String, Object> forward = plateRow(request.validationDate(), request.targetCode());
        if (base.isEmpty() || forward.isEmpty()) {
            return insufficient(request, "主线验证缺少板块日快照");
        }
        BigDecimal baseLimit = decimal(base.get("limit_up_count"));
        BigDecimal forwardLimit = decimal(forward.get("limit_up_count"));
        BigDecimal baseBroken = decimal(base.get("broken_limit_count"));
        BigDecimal forwardBroken = decimal(forward.get("broken_limit_count"));
        BigDecimal baseFlow = decimal(base.get("main_net_inflow"));
        BigDecimal forwardFlow = decimal(forward.get("main_net_inflow"));
        BigDecimal forwardMedian = decimal(forward.get("median_change_pct"));
        boolean hit = forwardLimit.compareTo(baseLimit.multiply(new BigDecimal("0.7000"))) >= 0
                && forwardBroken.compareTo(baseBroken.add(BigDecimal.ONE)) <= 0
                && forwardMedian.compareTo(new BigDecimal("-1.5000")) >= 0;
        boolean conflictHit = forwardLimit.compareTo(BigDecimal.ZERO) > 0 || forwardFlow.compareTo(baseFlow) >= 0;
        String signal = watchSignal(request) + "；limitUp " + baseLimit + "->" + forwardLimit
                + ", broken " + baseBroken + "->" + forwardBroken + ", flow " + baseFlow + "->" + forwardFlow
                + ", median=" + forwardMedian;
        return validationResult(request, hit, conflictHit, signal, closeReturn(request), maxDrawdown(request.targetType(), request.targetCode(), request.tradeDate(), request.validationDate(), safeBaseClose(request)));
    }

    private ForwardValidationResult validateLeader(ForwardValidationRequest request) {
        Map<String, Object> row = firstRow("""
                select limit_status, consecutive_limit_up_days, open_limit_times, seal_amount
                from stock_limit_status_daily
                where trade_date = ? and stock_code = ?
                limit 1
                """, request.validationDate(), request.targetCode());
        if (row.isEmpty()) {
            return insufficient(request, "龙头验证缺少个股涨停状态");
        }
        String status = text(row.get("limit_status"));
        BigDecimal height = decimal(row.get("consecutive_limit_up_days"));
        BigDecimal openTimes = decimal(row.get("open_limit_times"));
        boolean limitUp = "LIMIT_UP".equalsIgnoreCase(status);
        boolean broken = "BROKEN_LIMIT".equalsIgnoreCase(status) || "OPEN_LIMIT".equalsIgnoreCase(status);
        boolean hit = limitUp && openTimes.compareTo(new BigDecimal("3")) <= 0;
        boolean conflictHit = !broken && height.compareTo(BigDecimal.ZERO) > 0;
        String signal = watchSignal(request) + "；status=" + status + ", height=" + height + ", openTimes=" + openTimes;
        return validationResult(request, hit, conflictHit, signal, closeReturn(request), maxDrawdown(request.targetType(), request.targetCode(), request.tradeDate(), request.validationDate(), safeBaseClose(request)));
    }

    private ForwardValidationResult validateDivergenceConsensus(ForwardValidationRequest request) {
        BigDecimal brokenRisk = brokenLimitRisk(request);
        BigDecimal highFeedback = highPositionFeedback(request);
        BigDecimal plateMedian = decimal(firstValue("""
                select median_change_pct from plate_daily_snapshot
                where trade_date = ? and plate_code = ?
                limit 1
                """, request.validationDate(), request.targetCode()));
        boolean hit = brokenRisk.compareTo(new BigDecimal("0.3500")) <= 0
                && highFeedback.compareTo(new BigDecimal("0.4500")) <= 0
                && plateMedian.compareTo(new BigDecimal("-1.5000")) >= 0;
        boolean conflictHit = brokenRisk.compareTo(new BigDecimal("0.5000")) <= 0 || highFeedback.compareTo(new BigDecimal("0.5500")) <= 0;
        String signal = watchSignal(request) + "；brokenRisk=" + brokenRisk + ", highFeedback=" + highFeedback + ", plateMedian=" + plateMedian;
        return validationResult(request, hit, conflictHit, signal, closeReturn(request), maxDrawdown(request.targetType(), request.targetCode(), request.tradeDate(), request.validationDate(), safeBaseClose(request)));
    }

    private ForwardValidationResult validateRisk(ForwardValidationRequest request) {
        Map<String, Object> base = firstRow("""
                select loss_effect_score, limit_down_count, down_count, up_count
                from market_breadth_daily_snapshot
                where trade_date = ?
                limit 1
                """, request.tradeDate());
        Map<String, Object> forward = firstRow("""
                select loss_effect_score, limit_down_count, down_count, up_count
                from market_breadth_daily_snapshot
                where trade_date = ?
                limit 1
                """, request.validationDate());
        if (base.isEmpty() || forward.isEmpty()) {
            return insufficient(request, "风险验证缺少市场宽度事实");
        }
        BigDecimal baseLoss = decimal(base.get("loss_effect_score"));
        BigDecimal forwardLoss = decimal(forward.get("loss_effect_score"));
        BigDecimal baseLimitDown = decimal(base.get("limit_down_count"));
        BigDecimal forwardLimitDown = decimal(forward.get("limit_down_count"));
        BigDecimal forwardDrawdown = maxDrawdown(request.targetType(), request.targetCode(), request.tradeDate(), request.validationDate(), safeBaseClose(request));
        boolean riskContinued = forwardLoss.compareTo(baseLoss) > 0
                || forwardLimitDown.compareTo(baseLimitDown) > 0
                || forwardDrawdown.compareTo(new BigDecimal("-3.0000")) <= 0;
        boolean riskReduced = forwardLoss.compareTo(baseLoss) < 0 && forwardLimitDown.compareTo(baseLimitDown) <= 0;
        ValidationResultType type = riskContinued ? ValidationResultType.HIT : riskReduced ? ValidationResultType.MISS : ValidationResultType.CONFLICT_HIT;
        BigDecimal delta = switch (type) {
            case HIT -> BigDecimal.ONE;
            case CONFLICT_HIT -> new BigDecimal("0.5000");
            case MISS -> BigDecimal.ONE.negate();
            case INSUFFICIENT -> BigDecimal.ZERO;
        };
        String signal = watchSignal(request) + "；loss " + baseLoss + "->" + forwardLoss
                + ", limitDown " + baseLimitDown + "->" + forwardLimitDown + ", drawdown=" + forwardDrawdown;
        return result(request, type, signal, closeReturn(request), forwardDrawdown, delta);
    }

    private ValidationResultType validationType(EngineType engineType, BigDecimal returnPct, BigDecimal maxDrawdown) {
        if (engineType == EngineType.RISK) {
            if (maxDrawdown.compareTo(BigDecimal.valueOf(-5)) <= 0 || returnPct.compareTo(BigDecimal.valueOf(-3)) <= 0) {
                return ValidationResultType.HIT;
            }
            return ValidationResultType.MISS;
        }
        if (returnPct.compareTo(BigDecimal.valueOf(2)) >= 0 && maxDrawdown.compareTo(BigDecimal.valueOf(-6)) > 0) {
            return ValidationResultType.HIT;
        }
        if (returnPct.signum() >= 0) {
            return ValidationResultType.CONFLICT_HIT;
        }
        return ValidationResultType.MISS;
    }

    private BigDecimal closeOf(TargetType targetType, String targetCode, LocalDate tradeDate) {
        if (targetType == TargetType.PLATE) {
            return decimal(firstValue("select close_price from plate_daily_snapshot where trade_date = ? and plate_code = ? limit 1", tradeDate, targetCode));
        }
        if (targetType == TargetType.MARKET || !StringUtils.hasText(targetCode)) {
            return decimal(firstValue("select limit_up_count + up_count * 0.1 - down_count * 0.1 as market_score from market_breadth_daily_snapshot where trade_date = ? limit 1", tradeDate));
        }
        return decimal(firstValue("select close_price from stock_daily_kline where trade_date = ? and stock_code = ? limit 1", tradeDate, targetCode));
    }

    private ForwardValidationResult validationResult(ForwardValidationRequest request, boolean hit, boolean conflictHit,
                                                     String signal, BigDecimal returnPct, BigDecimal maxDrawdown) {
        ValidationResultType type = hit ? ValidationResultType.HIT : conflictHit ? ValidationResultType.CONFLICT_HIT : ValidationResultType.MISS;
        BigDecimal delta = switch (type) {
            case HIT -> BigDecimal.ONE;
            case CONFLICT_HIT -> new BigDecimal("0.5000");
            case MISS -> BigDecimal.ONE.negate();
            case INSUFFICIENT -> BigDecimal.ZERO;
        };
        return result(request, type, signal, returnPct, maxDrawdown, delta);
    }

    private ForwardValidationResult insufficient(ForwardValidationRequest request, String signal) {
        return result(request, ValidationResultType.INSUFFICIENT, watchSignal(request) + "；" + signal,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
    }

    private String watchSignal(ForwardValidationRequest request) {
        return "watch=" + safeText(request.watchId()) + "，" + safeText(request.watchTitle())
                + "，expected=" + safeText(request.expectedSignal()) + "，risk=" + safeText(request.riskSignal());
    }

    private BigDecimal closeReturn(ForwardValidationRequest request) {
        BigDecimal base = safeBaseClose(request);
        BigDecimal forward = closeOf(request.targetType(), request.targetCode(), request.validationDate());
        if (base.signum() <= 0 || forward.signum() <= 0) {
            return BigDecimal.ZERO;
        }
        return forward.subtract(base).multiply(BigDecimal.valueOf(100)).divide(base, 6, RoundingMode.HALF_UP);
    }

    private BigDecimal safeBaseClose(ForwardValidationRequest request) {
        return closeOf(request.targetType(), request.targetCode(), request.tradeDate());
    }

    private Map<String, Object> plateRow(LocalDate tradeDate, String plateCode) {
        return firstRow("""
                select close_price, limit_up_count, broken_limit_count, limit_down_count, main_net_inflow, median_change_pct
                from plate_daily_snapshot
                where trade_date = ? and plate_code = ?
                limit 1
                """, tradeDate, plateCode);
    }

    private BigDecimal breadthScore(Map<String, Object> row) {
        BigDecimal up = decimal(row.get("up_count"));
        BigDecimal down = decimal(row.get("down_count"));
        BigDecimal total = up.add(down);
        if (total.signum() <= 0) {
            return BigDecimal.ZERO;
        }
        return up.subtract(down).divide(total, 6, RoundingMode.HALF_UP);
    }

    private BigDecimal brokenLimitRisk(ForwardValidationRequest request) {
        if (request.targetType() == TargetType.PLATE && StringUtils.hasText(request.targetCode())) {
            Map<String, Object> row = plateRow(request.validationDate(), request.targetCode());
            BigDecimal broken = decimal(row.get("broken_limit_count"));
            BigDecimal limitUp = decimal(row.get("limit_up_count"));
            BigDecimal limitDown = decimal(row.get("limit_down_count"));
            BigDecimal total = broken.add(limitUp).add(limitDown);
            return total.signum() <= 0 ? BigDecimal.ZERO : broken.add(limitDown).divide(total, 6, RoundingMode.HALF_UP);
        }
        BigDecimal broken = decimal(firstValue("""
                select countIf(limit_status in ('BROKEN_LIMIT','OPEN_LIMIT')) from stock_limit_status_daily
                where trade_date = ?
                """, request.validationDate()));
        BigDecimal limitUp = decimal(firstValue("""
                select countIf(limit_status = 'LIMIT_UP') from stock_limit_status_daily
                where trade_date = ?
                """, request.validationDate()));
        BigDecimal total = broken.add(limitUp);
        return total.signum() <= 0 ? BigDecimal.ZERO : broken.divide(total, 6, RoundingMode.HALF_UP);
    }

    private BigDecimal highPositionFeedback(ForwardValidationRequest request) {
        Object value = firstValue("""
                select avg(impact_score) from high_position_feedback_daily
                where trade_date = ?
                """, request.validationDate());
        BigDecimal avg = decimal(value);
        if (avg.compareTo(BigDecimal.ONE) > 0) {
            return avg.divide(new BigDecimal("100"), 6, RoundingMode.HALF_UP);
        }
        return avg;
    }

    private Map<String, Object> firstRow(String sql, Object... args) {
        List<Map<String, Object>> rows = clickHouseJdbcTemplate.queryForList(sql, args);
        return rows.isEmpty() ? Map.of() : rows.getFirst();
    }

    private BigDecimal maxDrawdown(TargetType targetType, String targetCode, LocalDate start, LocalDate end, BigDecimal base) {
        String sql;
        Object[] args;
        if (targetType == TargetType.PLATE) {
            sql = "select min(close_price) from plate_daily_snapshot where trade_date between ? and ? and plate_code = ?";
            args = new Object[]{start, end, targetCode};
        } else if (targetType == TargetType.MARKET || !StringUtils.hasText(targetCode)) {
            sql = "select min(limit_up_count + up_count * 0.1 - down_count * 0.1) from market_breadth_daily_snapshot where trade_date between ? and ?";
            args = new Object[]{start, end};
        } else {
            sql = "select min(close_price) from stock_daily_kline where trade_date between ? and ? and stock_code = ?";
            args = new Object[]{start, end, targetCode};
        }
        BigDecimal min = decimal(firstValue(sql, args));
        if (base.signum() <= 0 || min.signum() <= 0) {
            return BigDecimal.ZERO;
        }
        return min.subtract(base).multiply(BigDecimal.valueOf(100)).divide(base, 6, RoundingMode.HALF_UP);
    }

    private Object firstValue(String sql, Object... args) {
        List<Map<String, Object>> rows = clickHouseJdbcTemplate.queryForList(sql, args);
        if (rows.isEmpty()) {
            return BigDecimal.ZERO;
        }
        return rows.getFirst().values().iterator().next();
    }

    private ForwardValidationResult result(ForwardValidationRequest request, ValidationResultType type, String signal,
                                           BigDecimal returnPct, BigDecimal maxDrawdown, BigDecimal scoreDelta) {
        return ForwardValidationResult.builder()
                .validationId("VAL-" + UUID.randomUUID())
                .judgementId(request.judgementId())
                .tradeDate(request.tradeDate())
                .validationDate(request.validationDate())
                .forwardDays(request.forwardDays())
                .engineType(request.engineType())
                .targetType(request.targetType())
                .targetCode(request.targetCode())
                .validationResult(type)
                .realizedSignal(signal)
                .returnPct(returnPct)
                .maxDrawdownPct(maxDrawdown)
                .scoreDelta(scoreDelta)
                .build();
    }

    private void save(ForwardValidationResult result) {
        String sql = """
                insert into judgement_forward_validation
                (validation_id, judgement_id, trade_date, validation_date, forward_days, engine_type, target_type,
                 target_code, validation_result, realized_signal, return_pct, max_drawdown_pct, score_delta)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
        clickHouseJdbcTemplate.update(
                sql,
                result.getValidationId(),
                result.getJudgementId(),
                Date.valueOf(result.getTradeDate()),
                Date.valueOf(result.getValidationDate()),
                result.getForwardDays(),
                result.getEngineType().name(),
                result.getTargetType().name(),
                result.getTargetCode(),
                result.getValidationResult().name(),
                result.getRealizedSignal(),
                result.getReturnPct(),
                result.getMaxDrawdownPct(),
                result.getScoreDelta()
        );
        saveEvidenceValidationItems(result);
    }

    private void saveEvidenceValidationItems(ForwardValidationResult result) {
        List<Map<String, Object>> evidenceRows = clickHouseJdbcTemplate.queryForList("""
                select evidence_id, factor_code, evidence_type
                from judgement_evidence_item
                where judgement_id = ?
                """, result.getJudgementId());
        if (evidenceRows.isEmpty()) {
            return;
        }
        String sql = """
                insert into evidence_validation_item
                (validation_id, evidence_id, judgement_id, trade_date, validation_date, factor_code,
                 evidence_type, validation_result, contribution_score)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
        for (Map<String, Object> row : evidenceRows) {
            clickHouseJdbcTemplate.update(
                    sql,
                    result.getValidationId(),
                    text(row.get("evidence_id")),
                    result.getJudgementId(),
                    Date.valueOf(result.getTradeDate()),
                    Date.valueOf(result.getValidationDate()),
                    text(row.get("factor_code")),
                    text(row.get("evidence_type")),
                    evidenceValidationResult(result, text(row.get("evidence_type"))),
                    evidenceContributionScore(result, text(row.get("evidence_type")))
            );
        }
    }

    private String evidenceValidationResult(ForwardValidationResult result, String evidenceType) {
        if (result.getValidationResult() == ValidationResultType.INSUFFICIENT) {
            return "INSUFFICIENT";
        }
        if ("CONFLICT".equals(evidenceType)) {
            return result.getValidationResult() == ValidationResultType.CONFLICT_HIT ? "CONFLICT_VALID" : "INVALID";
        }
        if ("WARNING".equals(evidenceType)) {
            return result.getValidationResult() == ValidationResultType.MISS ? "WARNING_VALID" : "VALID";
        }
        return result.getValidationResult() == ValidationResultType.HIT ? "VALID" : "INVALID";
    }

    private BigDecimal evidenceContributionScore(ForwardValidationResult result, String evidenceType) {
        if (result.getValidationResult() == ValidationResultType.INSUFFICIENT) {
            return BigDecimal.ZERO;
        }
        if ("CONFLICT".equals(evidenceType)) {
            return result.getValidationResult() == ValidationResultType.CONFLICT_HIT
                    ? BigDecimal.ONE
                    : new BigDecimal("-0.5000");
        }
        if ("WARNING".equals(evidenceType)) {
            return result.getValidationResult() == ValidationResultType.MISS
                    ? new BigDecimal("0.8000")
                    : new BigDecimal("0.2000");
        }
        return switch (result.getValidationResult()) {
            case HIT -> BigDecimal.ONE;
            case CONFLICT_HIT -> new BigDecimal("0.3000");
            case MISS -> BigDecimal.ONE.negate();
            case INSUFFICIENT -> BigDecimal.ZERO;
        };
    }

    private BigDecimal decimal(Object value) {
        if (value instanceof BigDecimal decimal) {
            return decimal;
        }
        if (value instanceof Number number) {
            return BigDecimal.valueOf(number.doubleValue());
        }
        if (value == null || !StringUtils.hasText(String.valueOf(value))) {
            return BigDecimal.ZERO;
        }
        return new BigDecimal(String.valueOf(value));
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private String safeText(String value) {
        return value == null ? "" : value;
    }
}
