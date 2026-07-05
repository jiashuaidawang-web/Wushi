package com.wushi.api.vo.common;

import com.wushi.common.enums.LeaderStatus;
import com.wushi.common.enums.LeaderType;

import java.math.BigDecimal;

public record LeaderMiniVO(
        String stockCode,
        String stockName,
        String plateCode,
        String plateName,
        LeaderType leaderType,
        LeaderStatus leaderStatus,
        BigDecimal positionScore,
        BigDecimal confidence
) {
}
