package com.wushi.module.review.model;

import com.wushi.common.enums.EvidenceLabelResult;

import java.time.LocalDateTime;

public record EvidenceLabelResultInfo(
        String labelId,
        String evidenceId,
        String judgementId,
        EvidenceLabelResult labelResult,
        LocalDateTime createdAt
) {
}
