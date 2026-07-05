package com.wushi.module.rule.factor;

import com.wushi.common.model.EvidenceItem;
import com.wushi.common.model.FactorResult;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class FactorCalculateResult {

    private String calculatorCode;
    private List<FactorResult> factorResults;
    private List<EvidenceItem> evidenceList;
    private List<EvidenceItem> conflictList;
    private List<EvidenceItem> warningList;
}
