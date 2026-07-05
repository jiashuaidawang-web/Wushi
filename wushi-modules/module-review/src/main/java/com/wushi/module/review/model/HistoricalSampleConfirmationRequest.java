package com.wushi.module.review.model;

import com.wushi.common.enums.SampleQuality;
import com.wushi.common.enums.SampleType;
import com.wushi.common.enums.TargetType;

import java.time.LocalDate;

public record HistoricalSampleConfirmationRequest(
        String sampleId,
        LocalDate tradeDate,
        TargetType targetType,
        String targetCode,
        SampleType sampleType,
        String confirmedLabel,
        SampleQuality sampleQuality,
        String confirmationDesc,
        String reviewer
) {
}
