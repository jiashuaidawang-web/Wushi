package com.wushi.common.model;

import com.wushi.common.enums.EngineType;
import com.wushi.common.enums.TargetType;
import com.wushi.common.enums.WatchValidationStatus;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;

@Data
@Builder
public class NextWatchItem {

    private String watchId;
    private String judgementId;
    private LocalDate tradeDate;
    private LocalDate watchDate;
    private EngineType engineType;
    private TargetType targetType;
    private String targetCode;
    private String targetName;
    private String watchCode;
    private String title;
    private String conditionExpression;
    private String expectedSignal;
    private String riskSignal;
    private Integer priority;
    private String ruleVersion;
    private WatchValidationStatus validationStatus;
}
