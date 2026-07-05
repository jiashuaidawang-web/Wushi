package com.wushi.module.review.model;

import com.wushi.common.enums.SampleQuality;
import com.wushi.common.enums.SampleType;

import java.time.LocalDateTime;

public record HistoricalSampleConfirmationResult(
        String sampleId,
        SampleType sampleType,
        String confirmedLabel,
        SampleQuality sampleQuality,
        LocalDateTime createdAt
) {
}
