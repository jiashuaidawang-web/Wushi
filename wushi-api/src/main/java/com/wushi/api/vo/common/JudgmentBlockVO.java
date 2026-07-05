package com.wushi.api.vo.common;

import java.util.List;

public record JudgmentBlockVO<T>(
        JudgmentMetaVO meta,
        String conclusion,
        T detail,
        List<EvidenceItemVO> evidenceList,
        List<EvidenceItemVO> conflictList,
        List<EvidenceItemVO> warningList,
        List<NextWatchItemVO> nextWatchList,
        List<DataQualityIssueVO> dataQualityIssues,
        List<ForwardValidationVO> forwardValidations
) {
}
