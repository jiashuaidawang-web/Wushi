package com.wushi.module.review.model;

import com.wushi.common.enums.EvidenceLabelResult;

import java.time.LocalDate;

public record EvidenceLabelRequest(
        String labelId,
        String evidenceId,
        String judgementId,
        LocalDate tradeDate,
        EvidenceLabelResult labelResult,
        String labelReason,
        String reviewer
) {
}
