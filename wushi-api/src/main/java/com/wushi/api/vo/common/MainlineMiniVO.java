package com.wushi.api.vo.common;

import com.wushi.common.enums.MainlineStatus;

import java.math.BigDecimal;

public record MainlineMiniVO(
        String plateCode,
        String plateName,
        String plateType,
        MainlineStatus status,
        BigDecimal strengthScore,
        BigDecimal confidence
) {
}
