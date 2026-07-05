package com.wushi.module.review.model;

import com.wushi.common.enums.CorrectionType;
import com.wushi.common.enums.EngineType;
import com.wushi.common.enums.JudgementMode;
import com.wushi.common.enums.TargetType;

import java.time.LocalDate;
import java.util.List;

public record ManualCorrectionRequest(
        String correctionId,
        LocalDate tradeDate,
        LocalDate asOfDate,
        JudgementMode judgementMode,
        EngineType engineType,
        TargetType targetType,
        String targetCode,
        String judgementId,
        CorrectionType correctionType,
        String correctionReason,
        String reviewer,
        List<CorrectionFieldItem> items
) {
}
