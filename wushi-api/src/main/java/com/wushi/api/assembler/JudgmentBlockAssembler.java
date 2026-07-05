package com.wushi.api.assembler;

import com.wushi.api.vo.common.DataQualityIssueVO;
import com.wushi.api.vo.common.EvidenceItemVO;
import com.wushi.api.vo.common.JudgmentBlockVO;
import com.wushi.api.vo.common.JudgmentMetaVO;
import com.wushi.api.vo.common.NextWatchItemVO;
import com.wushi.common.model.DataQualityContext;
import com.wushi.common.model.DataQualityIssue;
import com.wushi.common.model.EvidenceItem;
import com.wushi.common.model.JudgementResult;
import com.wushi.common.model.NextWatchItem;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class JudgmentBlockAssembler {

    public <T> JudgmentBlockVO<T> toBlock(JudgementResult<T> result) {
        return new JudgmentBlockVO<>(
                toMeta(result),
                result.getConclusion(),
                result.getDetail(),
                toEvidenceList(result.getEvidenceList()),
                toEvidenceList(result.getConflictList()),
                toEvidenceList(result.getWarningList()),
                toNextWatchList(result.getNextWatchList()),
                toDataQualityIssues(result.getDataQualityContext()),
                List.of()
        );
    }

    private JudgmentMetaVO toMeta(JudgementResult<?> result) {
        return new JudgmentMetaVO(
                result.getJudgementId(),
                result.getTradeDate(),
                result.getAsOfDate(),
                result.getJudgementMode(),
                result.getEngineType(),
                result.getTargetType(),
                result.getTargetCode(),
                result.getTargetName(),
                result.getConfidence(),
                result.getRuleVersion(),
                result.getDataQualityLevel()
        );
    }

    private List<EvidenceItemVO> toEvidenceList(List<EvidenceItem> items) {
        if (items == null) {
            return List.of();
        }
        return items.stream().map(this::toEvidence).toList();
    }

    private EvidenceItemVO toEvidence(EvidenceItem item) {
        return new EvidenceItemVO(
                item.getEvidenceCode(),
                item.getEvidenceType(),
                item.getEvidenceCode(),
                item.getTitle(),
                item.getTitle(),
                item.getDescription(),
                item.getScore(),
                item.getWeight(),
                item.getSourceTable(),
                item.getSourceKey(),
                item.getValidationStatus()
        );
    }

    private List<NextWatchItemVO> toNextWatchList(List<NextWatchItem> items) {
        if (items == null) {
            return List.of();
        }
        return items.stream().map(this::toNextWatch).toList();
    }

    private NextWatchItemVO toNextWatch(NextWatchItem item) {
        return new NextWatchItemVO(
                item.getWatchId(),
                item.getJudgementId(),
                item.getTradeDate(),
                item.getWatchDate(),
                item.getEngineType(),
                item.getTargetType(),
                item.getTargetCode(),
                item.getTargetName(),
                item.getTitle(),
                item.getConditionExpression(),
                item.getExpectedSignal(),
                item.getRiskSignal(),
                item.getPriority(),
                item.getRuleVersion(),
                item.getValidationStatus()
        );
    }

    private List<DataQualityIssueVO> toDataQualityIssues(DataQualityContext context) {
        if (context == null || context.getIssueList() == null) {
            return List.of();
        }
        return context.getIssueList().stream().map(this::toDataQualityIssue).toList();
    }

    private DataQualityIssueVO toDataQualityIssue(DataQualityIssue issue) {
        return new DataQualityIssueVO(
                issue.getIssueId(),
                issue.getDataDomain(),
                issue.getTableName(),
                issue.getIssueType(),
                issue.getSeverity(),
                issue.getImpactLevel(),
                issue.getImpactPages(),
                issue.getConfidencePenalty(),
                issue.getDescription()
        );
    }
}
