package com.wushi.common.model;

import com.wushi.common.enums.EvidenceType;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class FactorResult {

    private String factorCode;
    private String factorName;
    private BigDecimal factorValue;
    private BigDecimal score;
    private BigDecimal weight;
    private EvidenceType evidenceType;
    private String description;
}
