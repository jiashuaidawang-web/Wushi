package com.wushi.common.model;

import com.wushi.common.enums.DataQualityLevel;
import com.wushi.common.enums.EngineType;
import com.wushi.common.enums.JudgementMode;
import com.wushi.common.enums.TargetType;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Data
@Builder
public class JudgementResult<T> {

    private String judgementId;
    private LocalDate tradeDate;
    private LocalDate asOfDate;
    private JudgementMode judgementMode;
    private EngineType engineType;
    private TargetType targetType;
    private String targetCode;
    private String targetName;
    private String conclusion;
    private BigDecimal confidence;
    private String ruleVersion;
    private DataQualityLevel dataQualityLevel;
    private DataQualityContext dataQualityContext;
    private T detail;
    private List<EvidenceItem> evidenceList;
    private List<EvidenceItem> conflictList;
    private List<EvidenceItem> warningList;
    private List<NextWatchItem> nextWatchList;
}
