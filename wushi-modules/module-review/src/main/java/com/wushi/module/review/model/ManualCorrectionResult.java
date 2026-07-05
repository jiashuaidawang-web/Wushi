package com.wushi.module.review.model;

import com.wushi.common.enums.CorrectionStatus;

import java.time.LocalDateTime;

public record ManualCorrectionResult(
        String correctionId,
        CorrectionStatus status,
        Integer itemCount,
        LocalDateTime createdAt
) {
}
