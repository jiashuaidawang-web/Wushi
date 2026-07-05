package com.wushi.common.model;

import com.wushi.common.enums.EngineType;
import com.wushi.common.enums.TargetType;
import com.wushi.common.enums.ValidationResultType;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@Builder
public class ForwardValidationResult {

    private String validationId;
    private String judgementId;
    private LocalDate tradeDate;
    private LocalDate validationDate;
    private Integer forwardDays;
    private EngineType engineType;
    private TargetType targetType;
    private String targetCode;
    private ValidationResultType validationResult;
    private String realizedSignal;
    private BigDecimal returnPct;
    private BigDecimal maxDrawdownPct;
    private BigDecimal scoreDelta;
}
