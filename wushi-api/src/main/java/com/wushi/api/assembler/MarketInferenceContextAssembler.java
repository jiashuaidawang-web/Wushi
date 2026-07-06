package com.wushi.api.assembler;

import com.wushi.api.vo.common.DataQualityIssueVO;
import com.wushi.api.vo.common.JudgmentBlockVO;
import com.wushi.api.vo.common.MarketQuery;
import com.wushi.api.vo.common.NextWatchItemVO;
import com.wushi.api.vo.page.MarketInferenceContextVO;
import com.wushi.common.model.JudgementResult;
import com.wushi.module.agent.audit.inference.MarketInferenceContext;
import com.wushi.module.emotion.model.CycleJudgementDetail;
import com.wushi.module.leader.model.LeaderJudgementDetail;
import com.wushi.module.mainline.model.MainlineJudgementDetail;
import com.wushi.module.pattern.model.DivergenceConsensusDetail;
import com.wushi.module.risk.model.RiskRadarDetail;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
public class MarketInferenceContextAssembler {

    private static final String CHAIN_TITLE = "市场周期与主线龙头推演链";
    private static final String CHAIN_SLOGAN = "用周期判断时，用主线判断机，用龙头判断势，用分歧一致判断点，用风险模型判断退";

    private final JudgmentBlockAssembler judgmentBlockAssembler;

    public MarketInferenceContextVO toVO(MarketQuery query, MarketInferenceContext context) {
        JudgmentBlockVO<CycleJudgementDetail> cycleCard = block(context.cycleResult());
        List<JudgmentBlockVO<MainlineJudgementDetail>> mainlineCards = context.mainlineResults().stream()
                .map(result -> block(result))
                .toList();
        List<JudgmentBlockVO<LeaderJudgementDetail>> leaderCards = context.leaderResults().stream()
                .map(result -> block(result))
                .toList();
        JudgmentBlockVO<DivergenceConsensusDetail> divergenceCard = block(context.divergenceResult());
        JudgmentBlockVO<RiskRadarDetail> riskCard = block(context.riskResult());

        List<NextWatchItemVO> nextWatchList = new ArrayList<>();
        List<DataQualityIssueVO> dataQualityIssues = new ArrayList<>();
        collect(nextWatchList, dataQualityIssues, cycleCard);
        mainlineCards.forEach(card -> collect(nextWatchList, dataQualityIssues, card));
        leaderCards.forEach(card -> collect(nextWatchList, dataQualityIssues, card));
        collect(nextWatchList, dataQualityIssues, divergenceCard);
        collect(nextWatchList, dataQualityIssues, riskCard);

        return new MarketInferenceContextVO(
                query,
                chain(context),
                chainNodes(context),
                cycleCard,
                mainlineCards,
                leaderCards,
                divergenceCard,
                riskCard,
                context.topMainline().map(this::mainlineSummary).orElse(null),
                context.topLeader().map(this::leaderSummary).orElse(null),
                nextWatchList,
                dataQualityIssues
        );
    }

    private MarketInferenceContextVO.InferenceChainVO chain(MarketInferenceContext context) {
        CycleJudgementDetail cycle = detail(context.cycleResult());
        MainlineJudgementDetail mainline = context.topMainline().map(JudgementResult::getDetail).orElse(null);
        LeaderJudgementDetail leader = context.topLeader().map(JudgementResult::getDetail).orElse(null);
        DivergenceConsensusDetail divergence = detail(context.divergenceResult());
        RiskRadarDetail risk = detail(context.riskResult());
        return new MarketInferenceContextVO.InferenceChainVO(
                CHAIN_TITLE,
                CHAIN_SLOGAN,
                cycle == null ? null : enumName(cycle.marketCycleStage()) + "/" + enumName(cycle.emotionCycleStage()),
                cycle == null ? null : cycle.strategyBoundary(),
                cycle == null ? null : cycle.allowedMode(),
                mainline == null ? "暂无主线候选" : enumName(mainline.mainlineStatus()) + "/" + enumName(mainline.lifecycleStage()),
                mainline == null ? null : mainline.participationDecision(),
                leader == null ? "暂无龙头候选" : enumName(leader.leaderType()) + "/" + enumName(leader.leaderStatus()),
                divergence == null ? null : enumName(divergence.state()),
                risk == null ? null : enumName(risk.riskLevel()) + "/" + enumName(risk.riskType()),
                tomorrowValidation(mainline, leader, divergence)
        );
    }

    private String tomorrowValidation(MainlineJudgementDetail mainline, LeaderJudgementDetail leader, DivergenceConsensusDetail divergence) {
        if (mainline != null && hasText(mainline.tomorrowValidation())) {
            return mainline.tomorrowValidation();
        }
        if (leader != null && hasText(leader.tomorrowValidation())) {
            return leader.tomorrowValidation();
        }
        if (divergence != null && hasText(divergence.tomorrowValidation())) {
            return divergence.tomorrowValidation();
        }
        return null;
    }

    private List<MarketInferenceContextVO.InferenceNodeVO> chainNodes(MarketInferenceContext context) {
        List<MarketInferenceContextVO.InferenceNodeVO> nodes = new ArrayList<>();
        addNode(nodes, "CYCLE", "周期识别", context.cycleResult());
        context.mainlineResults().forEach(result -> addNode(nodes, "MAINLINE", "主线识别", result));
        context.leaderResults().forEach(result -> addNode(nodes, "LEADER", "龙头竞争", result));
        addNode(nodes, "DIVERGENCE_CONSENSUS", "分歧一致", context.divergenceResult());
        addNode(nodes, "RISK", "风险兑现", context.riskResult());
        return nodes;
    }

    private void addNode(List<MarketInferenceContextVO.InferenceNodeVO> nodes, String nodeCode, String nodeName, JudgementResult<?> result) {
        if (result == null) {
            return;
        }
        nodes.add(new MarketInferenceContextVO.InferenceNodeVO(
                nodeCode,
                nodeName,
                result.getTargetCode(),
                result.getTargetName(),
                result.getConclusion(),
                result.getConfidence(),
                result.getRuleVersion(),
                size(result.getEvidenceList()),
                size(result.getConflictList()),
                size(result.getWarningList()),
                size(result.getNextWatchList())
        ));
    }

    private MarketInferenceContextVO.MainlineSummaryVO mainlineSummary(JudgementResult<MainlineJudgementDetail> result) {
        MainlineJudgementDetail detail = result.getDetail();
        if (detail == null) {
            return null;
        }
        return new MarketInferenceContextVO.MainlineSummaryVO(
                detail.plateCode(),
                detail.plateName(),
                detail.plateType(),
                detail.candidateRank(),
                detail.candidateScore(),
                enumName(detail.mainlineStatus()),
                enumName(detail.lifecycleStage()),
                detail.lifecycleStageName(),
                detail.participationDecision(),
                detail.unmetCondition(),
                detail.tomorrowValidation()
        );
    }

    private MarketInferenceContextVO.LeaderSummaryVO leaderSummary(JudgementResult<LeaderJudgementDetail> result) {
        LeaderJudgementDetail detail = result.getDetail();
        if (detail == null) {
            return null;
        }
        return new MarketInferenceContextVO.LeaderSummaryVO(
                detail.stockCode(),
                detail.stockName(),
                detail.plateCode(),
                detail.plateName(),
                detail.candidateRank(),
                enumName(detail.leaderType()),
                enumName(detail.leaderStatus()),
                detail.positionScore(),
                detail.driveScore(),
                detail.divergenceRepairScore(),
                detail.challengeRiskScore(),
                detail.unmetCondition(),
                detail.tomorrowValidation()
        );
    }

    private <T> JudgmentBlockVO<T> block(JudgementResult<T> result) {
        return result == null ? null : judgmentBlockAssembler.toBlock(result);
    }

    private void collect(List<NextWatchItemVO> nextWatchList, List<DataQualityIssueVO> dataQualityIssues, JudgmentBlockVO<?> block) {
        if (block == null) {
            return;
        }
        if (block.nextWatchList() != null) {
            nextWatchList.addAll(block.nextWatchList());
        }
        if (block.dataQualityIssues() != null) {
            dataQualityIssues.addAll(block.dataQualityIssues());
        }
    }

    private <T> T detail(JudgementResult<T> result) {
        return result == null ? null : result.getDetail();
    }

    private String enumName(Enum<?> value) {
        return value == null ? null : value.name();
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private int size(List<?> items) {
        return items == null ? 0 : items.size();
    }
}
