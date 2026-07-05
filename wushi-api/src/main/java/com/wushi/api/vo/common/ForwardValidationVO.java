package com.wushi.api.vo.common;

import com.wushi.common.enums.ValidationResultType;

import java.math.BigDecimal;
import java.time.LocalDate;

public record ForwardValidationVO(
        String validationId,
        String judgementId,
        LocalDate tradeDate,
        LocalDate validationDate,
        Integer forwardDays,
        ValidationResultType validationResult,
        String realizedSignal,
        BigDecimal returnPct,
        BigDecimal maxDrawdownPct,
        BigDecimal scoreDelta
) {
}
