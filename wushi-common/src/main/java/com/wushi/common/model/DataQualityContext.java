package com.wushi.common.model;

import com.wushi.common.enums.DataQualityLevel;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Data
@Builder
public class DataQualityContext {

    private LocalDate tradeDate;
    private DataQualityLevel level;
    private BigDecimal confidencePenalty;
    private List<DataQualityIssue> issueList;
}
