package com.wushi.common.model;

import com.wushi.common.enums.EvidenceType;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class EvidenceItem {

    private String evidenceCode;
    private EvidenceType evidenceType;
    private String title;
    private String description;
    private BigDecimal score;
    private BigDecimal weight;
    private String sourceTable;
    private String sourceKey;
}
