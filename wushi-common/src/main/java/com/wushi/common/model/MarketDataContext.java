package com.wushi.common.model;

import com.wushi.common.enums.JudgementMode;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;

@Data
@Builder
public class MarketDataContext {

    private LocalDate tradeDate;
    private LocalDate asOfDate;
    private JudgementMode judgementMode;
    private String ruleVersion;
    private DataQualityContext dataQualityContext;
}
