package com.wushi.api.vo.common;

import com.wushi.common.enums.EvidenceType;

import java.math.BigDecimal;

public record EvidenceItemVO(
        String evidenceId,
        EvidenceType evidenceType,
        String factorCode,
        String factorName,
        String title,
        String description,
        BigDecimal score,
        BigDecimal weight,
        String sourceTable,
        String sourceKey,
        String validationStatus
) {
}
