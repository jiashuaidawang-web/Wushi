package com.wushi.module.risk.model;

import com.wushi.common.enums.RiskLevel;
import com.wushi.common.enums.RiskType;
import com.wushi.common.enums.TargetType;

import java.math.BigDecimal;

public record RiskRadarDetail(
        TargetType targetType,
        String targetCode,
        String targetName,
        RiskLevel riskLevel,
        RiskType riskType,
        BigDecimal riskScore,
        BigDecimal highPositionFeedbackScore,
        BigDecimal brokenLimitScore,
        BigDecimal lossSpreadScore,
        String riskReason,
        String reduceRiskSignal
) {
}
