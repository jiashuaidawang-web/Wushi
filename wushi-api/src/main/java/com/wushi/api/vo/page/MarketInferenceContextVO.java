package com.wushi.api.vo.page;

import com.wushi.api.vo.common.DataQualityIssueVO;
import com.wushi.api.vo.common.JudgmentBlockVO;
import com.wushi.api.vo.common.MarketQuery;
import com.wushi.api.vo.common.NextWatchItemVO;
import com.wushi.module.emotion.model.CycleJudgementDetail;
import com.wushi.module.leader.model.LeaderJudgementDetail;
import com.wushi.module.mainline.model.MainlineJudgementDetail;
import com.wushi.module.pattern.model.DivergenceConsensusDetail;
import com.wushi.module.risk.model.RiskRadarDetail;

import java.math.BigDecimal;
import java.util.List;

public record MarketInferenceContextVO(
        MarketQuery query,
        InferenceChainVO chain,
        List<InferenceNodeVO> chainNodes,
        JudgmentBlockVO<CycleJudgementDetail> cycleCard,
        List<JudgmentBlockVO<MainlineJudgementDetail>> mainlineCards,
        List<JudgmentBlockVO<LeaderJudgementDetail>> leaderCards,
        JudgmentBlockVO<DivergenceConsensusDetail> divergenceCard,
        JudgmentBlockVO<RiskRadarDetail> riskCard,
        MainlineSummaryVO topMainline,
        LeaderSummaryVO topLeader,
        List<NextWatchItemVO> nextWatchList,
        List<DataQualityIssueVO> dataQualityIssues
) {

    public record InferenceChainVO(
            String chainTitle,
            String chainSlogan,
            String currentStage,
            String strategyBoundary,
            String allowedMode,
            String opportunityState,
            String participationDecision,
            String leaderState,
            String patternState,
            String riskState,
            String tomorrowValidation
    ) {
    }

    public record InferenceNodeVO(
            String nodeCode,
            String nodeName,
            String targetCode,
            String targetName,
            String conclusion,
            BigDecimal confidence,
            String ruleVersion,
            Integer evidenceCount,
            Integer conflictCount,
            Integer warningCount,
            Integer nextWatchCount
    ) {
    }

    public record MainlineSummaryVO(
            String plateCode,
            String plateName,
            String plateType,
            Integer candidateRank,
            BigDecimal candidateScore,
            String mainlineStatus,
            String lifecycleStage,
            String lifecycleStageName,
            String participationDecision,
            String unmetCondition,
            String tomorrowValidation
    ) {
    }

    public record LeaderSummaryVO(
            String stockCode,
            String stockName,
            String plateCode,
            String plateName,
            Integer candidateRank,
            String leaderType,
            String leaderStatus,
            BigDecimal positionScore,
            BigDecimal driveScore,
            BigDecimal divergenceRepairScore,
            BigDecimal challengeRiskScore,
            String unmetCondition,
            String tomorrowValidation
    ) {
    }
}
