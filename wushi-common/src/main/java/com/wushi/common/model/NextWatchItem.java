package com.wushi.common.model;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;

@Data
@Builder
public class NextWatchItem {

    private LocalDate watchDate;
    private String watchCode;
    private String title;
    private String conditionExpression;
    private String expectedSignal;
    private String riskSignal;
}
