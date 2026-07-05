package com.wushi.module.leader.engine.impl;

import com.wushi.common.enums.EngineType;
import com.wushi.common.enums.EvidenceType;
import com.wushi.common.enums.JudgementMode;
import com.wushi.common.enums.LeaderStatus;
import com.wushi.common.enums.LeaderType;
import com.wushi.common.enums.TargetType;
import com.wushi.common.enums.WatchValidationStatus;
import com.wushi.common.model.FactorResult;
import com.wushi.common.model.JudgementResult;
import com.wushi.common.model.NextWatchItem;
import com.wushi.module.leader.engine.LeaderCompetitionEngine;
import com.wushi.module.leader.factor.LeaderFactorCalculator;
import com.wushi.module.leader.model.LeaderJudgementDetail;
import com.wushi.module.market.enums.FactTable;
import com.wushi.module.market.service.MarketFactService;
import com.wushi.module.rule.engine.core.EngineRequest;
import com.wushi.module.rule.factor.FactorCalculateRequest;
import com.wushi.module.rule.factor.FactorCalculateResult;
import com.wushi.module.rule.support.JudgementResultPostProcessor;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class DefaultLeaderCompetitionEngine implements LeaderCompetitionEngine {

    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP);
    private static final BigDecimal ONE = BigDecimal.ONE.setScale(4, RoundingMode.HALF_UP);

    private final LeaderFactorCalculator leaderFactorCalculator;
    private final JudgementResultPostProcessor resultPostProcessor;
    private final MarketFactService marketFactService;

    @Override
    public JudgementResult<LeaderJudgementDetail> judge(EngineRequest request) {
        List<JudgementResult<LeaderJudgementDetail>> candidates = judgeCandidates(request, 1);
        if (!candidates.isEmpty()) {
            return candidates.get(0);
        }
        CandidateIdentity candidate = CandidateIdentity.empty();
        EngineRequest candidateRequest = withCandidate(request, candidate, Map.of());
        FactorCalculateResult factorResult = leaderFactorCalculator.calculate(toFactorRequest(candidateRequest, candidate));
        CandidateEvaluation evaluation = new CandidateEvaluation(candidate, factorResult, ZERO, 1, 1);
        LeaderDecision decision = decideLeader(evaluation, List.of(evaluation));
        return buildJudgementResult(request, candidateRequest, evaluation, CandidateIdentity.empty(), decision);
    }

    @Override
    public List<JudgementResult<LeaderJudgementDetail>> judgeCandidates(EngineRequest request, int candidateLimit) {
        List<CandidateIdentity> candidates = resolveCandidates(request, safeLimit(candidateLimit));
        if (candidates.isEmpty()) {
            candidates = List.of(resolveCandidate(request));
        }

        List<CandidateEvaluation> evaluations = new ArrayList<>();
        for (CandidateIdentity candidate : candidates) {
            EngineRequest candidateRequest = withCandidate(request, candidate, Map.of());
            FactorCalculateResult factorResult = leaderFactorCalculator.calculate(toFactorRequest(candidateRequest, candidate));
            evaluations.add(new CandidateEvaluation(candidate, factorResult, competitionScore(factorResult.getFactorResults()), 0, 0));
        }

        List<CandidateEvaluation> ranked = rankEvaluations(evaluations);
        List<JudgementResult<LeaderJudgementDetail>> results = new ArrayList<>();
        for (CandidateEvaluation evaluation : ranked) {
            Map<String, Object> rankParams = Map.of(
                    "candidateRank", evaluation.marketRank(),
                    "candidateScore", evaluation.competitionScore(),
                    "samePlateRank", evaluation.samePlateRank()
            );
            EngineRequest candidateRequest = withCandidate(request, evaluation.candidate(), rankParams);
            LeaderDecision decision = decideLeader(evaluation, ranked);
            CandidateIdentity challenger = resolveChallenger(ranked, evaluation);
            results.add(buildJudgementResult(request, candidateRequest, evaluation, challenger, decision));
        }
        return results;
    }

    private JudgementResult<LeaderJudgementDetail> buildJudgementResult(EngineRequest request, EngineRequest candidateRequest,
                                                                        CandidateEvaluation evaluation, CandidateIdentity challenger,
                                                                        LeaderDecision decision) {
        CandidateIdentity candidate = evaluation.candidate();
        FactorCalculateResult factorResult = evaluation.factorResult();
        String judgementId = "LEADER-" + request.tradeDate() + "-" + defaultText(candidate.stockCode(), "AUTO") + "-" + UUID.randomUUID();

        JudgementResult<LeaderJudgementDetail> result = JudgementResult.<LeaderJudgementDetail>builder()
                .judgementId(judgementId)
                .tradeDate(request.tradeDate())
                .asOfDate(request.asOfDate())
                .judgementMode(defaultMode(request.judgementMode()))
                .engineType(EngineType.LEADER)
                .targetType(TargetType.STOCK)
                .targetCode(candidate.stockCode())
                .targetName(candidate.stockName())
                .conclusion(decision.conclusion())
                .confidence(decision.confidence())
                .ruleVersion(request.ruleVersion())
                .dataQualityContext(request.dataQualityContext())
                .detail(new LeaderJudgementDetail(
                        candidate.stockCode(),
                        candidate.stockName(),
                        candidate.plateCode(),
                        candidate.plateName(),
                        decision.leaderType(),
                        decision.leaderStatus(),
                        value(factorResult.getFactorResults(), "LEADER_POSITION_SCORE"),
                        value(factorResult.getFactorResults(), "LEADER_POPULARITY_SCORE"),
                        value(factorResult.getFactorResults(), "LEADER_DRIVE_SCORE"),
                        value(factorResult.getFactorResults(), "LEADER_DIVERGENCE_REPAIR"),
                        value(factorResult.getFactorResults(), "LEADER_CHALLENGE_RISK"),
                        challenger.stockCode(),
                        challenger.stockName(),
                        factorResult.getFactorResults(),
                        decision.satisfiedConditions(),
                        decision.unmetCondition(),
                        decision.tomorrowValidation(),
                        decision.leaderReason(),
                        decision.leaderRisk()
                ))
                .evidenceList(factorResult.getEvidenceList())
                .conflictList(factorResult.getConflictList())
                .warningList(factorResult.getWarningList())
                .nextWatchList(buildNextWatchList(judgementId, candidateRequest, candidate, challenger, decision))
                .build();

        return resultPostProcessor.apply(candidateRequest, result);
    }

    private FactorCalculateRequest toFactorRequest(EngineRequest request, CandidateIdentity candidate) {
        return FactorCalculateRequest.builder()
                .tradeDate(request.tradeDate())
                .asOfDate(request.asOfDate())
                .engineType(EngineType.LEADER)
                .targetCode(candidate.stockCode())
                .targetName(candidate.stockName())
                .ruleContext(request.ruleContext())
                .facts(request.params())
                .params(mergeParams(request.params(), candidate))
                .build();
    }

    private LeaderDecision decideLeader(CandidateEvaluation evaluation, List<CandidateEvaluation> rankedCandidates) {
        List<FactorResult> factors = evaluation.factorResult().getFactorResults();
        CandidateIdentity candidate = evaluation.candidate();
        BigDecimal position = defaultZero(value(factors, "LEADER_POSITION_SCORE"));
        BigDecimal height = defaultZero(value(factors, "LEADER_CONSECUTIVE_LIMIT_DAYS"));
        BigDecimal drive = defaultZero(value(factors, "LEADER_DRIVE_SCORE"));
        BigDecimal popularity = defaultZero(value(factors, "LEADER_POPULARITY_SCORE"));
        BigDecimal repair = defaultZero(value(factors, "LEADER_DIVERGENCE_REPAIR"));
        BigDecimal challengeRisk = defaultZero(value(factors, "LEADER_CHALLENGE_RISK"));
        CandidateEvaluation marketLeader = rankedCandidates.isEmpty() ? evaluation : rankedCandidates.get(0);
        BigDecimal leaderGap = marketLeader.competitionScore().subtract(evaluation.competitionScore()).max(ZERO);

        List<String> satisfied = new ArrayList<>();
        List<String> unmet = new ArrayList<>();
        collect(position.compareTo(new BigDecimal("0.6500")) >= 0, "地位分进入龙头区", "地位分不足，暂未完成上位", satisfied, unmet);
        collect(height.compareTo(new BigDecimal("3")) >= 0, "连板高度形成空间锚", "高度不足，仍偏试错或跟风", satisfied, unmet);
        collect(drive.compareTo(new BigDecimal("0.5500")) >= 0, "带动主线或分支扩散", "带动性不足，板块合力未跟上", satisfied, unmet);
        collect(popularity.compareTo(new BigDecimal("0.5500")) >= 0, "人气与资金关注达标", "人气热度不足，接力意愿不强", satisfied, unmet);
        collect(repair.compareTo(new BigDecimal("0.5000")) >= 0, "分歧后具备修复能力", "分歧修复弱，容易兑现为风险", satisfied, unmet);
        collect(challengeRisk.compareTo(new BigDecimal("0.5000")) < 0, "同板块挑战压力可控", "同板块存在明显挑战压力", satisfied, unmet);
        collect(evaluation.marketRank() == 1 || leaderGap.compareTo(new BigDecimal("0.1200")) <= 0,
                "全市场竞争排序靠前", "全市场竞争排序落后，暂不能定义为总龙", satisfied, unmet);
        collect(evaluation.samePlateRank() == 1,
                "同板块竞争排序第一", "同板块内仍有更强竞争者", satisfied, unmet);

        LeaderType leaderType = decideLeaderType(evaluation, rankedCandidates, position, height, drive, popularity, repair, challengeRisk);
        LeaderStatus leaderStatus = decideLeaderStatus(evaluation, rankedCandidates, position, height, repair, challengeRisk);
        BigDecimal confidence = confidence(score(factors), countEvidence(factors, EvidenceType.CONFLICT), countEvidence(factors, EvidenceType.WARNING), candidate.stockCode());
        String reason = buildReason(leaderType, leaderStatus, evaluation, marketLeader, position, height, drive, popularity, repair);
        String risk = buildRisk(leaderStatus, challengeRisk, repair, drive);
        String validation = buildTomorrowValidation(leaderType, leaderStatus);
        String conclusion = buildConclusion(candidate, leaderType, leaderStatus, reason, risk);

        return new LeaderDecision(
                leaderType,
                leaderStatus,
                confidence,
                conclusion,
                satisfied,
                String.join("、", unmet),
                validation,
                reason,
                risk
        );
    }

    private LeaderType decideLeaderType(CandidateEvaluation evaluation, List<CandidateEvaluation> rankedCandidates,
                                        BigDecimal position, BigDecimal height, BigDecimal drive, BigDecimal popularity, BigDecimal repair,
                                        BigDecimal challengeRisk) {
        CandidateIdentity candidate = evaluation.candidate();
        if (!StringUtils.hasText(candidate.stockCode())) {
            return LeaderType.UNKNOWN;
        }
        CandidateEvaluation marketLeader = rankedCandidates.isEmpty() ? evaluation : rankedCandidates.get(0);
        BigDecimal leaderGap = marketLeader.competitionScore().subtract(evaluation.competitionScore()).max(ZERO);
        boolean marketTop = evaluation.marketRank() == 1;
        boolean closeToMarketTop = leaderGap.compareTo(new BigDecimal("0.0800")) <= 0;
        boolean plateTop = evaluation.samePlateRank() == 1;
        if (height.compareTo(new BigDecimal("7")) >= 0 && popularity.compareTo(new BigDecimal("0.6500")) >= 0
                && drive.compareTo(new BigDecimal("0.3500")) < 0) {
            return LeaderType.MONSTER;
        }
        if (height.compareTo(new BigDecimal("2")) < 0 && popularity.compareTo(new BigDecimal("0.5500")) >= 0
                && repair.compareTo(new BigDecimal("0.5500")) >= 0) {
            return LeaderType.TREND;
        }
        if (marketTop && position.compareTo(new BigDecimal("0.6200")) >= 0 && drive.compareTo(new BigDecimal("0.5500")) >= 0
                && challengeRisk.compareTo(new BigDecimal("0.5500")) < 0) {
            return LeaderType.MARKET;
        }
        if (closeToMarketTop && plateTop && position.compareTo(new BigDecimal("0.5800")) >= 0
                && drive.compareTo(new BigDecimal("0.5000")) >= 0) {
            return LeaderType.MAINLINE;
        }
        if (plateTop && position.compareTo(new BigDecimal("0.4500")) >= 0 && drive.compareTo(new BigDecimal("0.3600")) >= 0) {
            return LeaderType.BRANCH;
        }
        if (height.compareTo(new BigDecimal("2")) < 0 && popularity.compareTo(new BigDecimal("0.5000")) >= 0
                && drive.compareTo(new BigDecimal("0.3500")) >= 0) {
            return LeaderType.MIDDLE_ARMY;
        }
        if (position.compareTo(new BigDecimal("0.3500")) >= 0 || popularity.compareTo(new BigDecimal("0.3500")) >= 0) {
            return LeaderType.FOLLOWER;
        }
        return LeaderType.UNKNOWN;
    }

    private LeaderStatus decideLeaderStatus(CandidateEvaluation evaluation, List<CandidateEvaluation> rankedCandidates,
                                            BigDecimal position, BigDecimal height, BigDecimal repair, BigDecimal challengeRisk) {
        CandidateEvaluation samePlateLeader = samePlateLeader(rankedCandidates, evaluation);
        boolean challengedBySamePlate = samePlateLeader != evaluation
                && samePlateLeader.competitionScore().subtract(evaluation.competitionScore()).compareTo(new BigDecimal("0.0500")) >= 0;
        if (position.compareTo(new BigDecimal("0.1500")) < 0 && height.compareTo(BigDecimal.ONE) < 0) {
            return LeaderStatus.DEAD;
        }
        if (position.compareTo(new BigDecimal("0.3000")) < 0 || repair.compareTo(new BigDecimal("0.2500")) < 0) {
            return LeaderStatus.DROPPED;
        }
        if (challengeRisk.compareTo(new BigDecimal("0.6000")) >= 0 || challengedBySamePlate) {
            return LeaderStatus.CHALLENGED;
        }
        if (evaluation.marketRank() == 1 && position.compareTo(new BigDecimal("0.6500")) >= 0
                && repair.compareTo(new BigDecimal("0.6000")) >= 0) {
            return LeaderStatus.STABLE;
        }
        if (position.compareTo(new BigDecimal("0.5000")) >= 0 && height.compareTo(new BigDecimal("2")) >= 0) {
            return LeaderStatus.RISING;
        }
        if (position.compareTo(new BigDecimal("0.3000")) >= 0) {
            return LeaderStatus.CANDIDATE;
        }
        return LeaderStatus.UNKNOWN;
    }

    private List<NextWatchItem> buildNextWatchList(String judgementId, EngineRequest request, CandidateIdentity candidate,
                                                   CandidateIdentity challenger, LeaderDecision decision) {
        LocalDate watchDate = request.tradeDate() == null ? null : request.tradeDate().plusDays(1);
        String challengerName = StringUtils.hasText(challenger.stockName()) ? challenger.stockName() : "潜在挑战者";
        return List.of(
                watch(judgementId, request, watchDate, candidate, "LEADER_WATCH_POSITION",
                        "验证龙头地位是否继续上位", "候选股继续晋级或分歧后回封，并能带动所属方向保持梯队",
                        "上位验证通过，支持龙头状态维持或升级", "断板无修复且板块跟随走弱，龙头地位降级", 1),
                watch(judgementId, request, watchDate, candidate, "LEADER_WATCH_DIVERGENCE_REPAIR",
                        "验证分歧修复能力", decision.tomorrowValidation(),
                        "分歧后能弱转强或回封，说明势仍在", "高开兑现、炸板不回封或低开低走，说明风险开始兑现", 1),
                watch(judgementId, request, watchDate, candidate, "LEADER_WATCH_CHALLENGER",
                        "验证挑战者是否夺位", challengerName + "若高度、人气或带动性超过当前候选，则确认龙头竞争切换",
                        "挑战未能扩大，当前候选保留主导权", "挑战者完成卡位，当前候选降为被挑战或跟风", 2)
        );
    }

    private NextWatchItem watch(String judgementId, EngineRequest request, LocalDate watchDate, CandidateIdentity candidate,
                                String watchCode, String title, String condition, String expectedSignal,
                                String riskSignal, int priority) {
        return NextWatchItem.builder()
                .watchId(watchCode + "-" + request.tradeDate() + "-" + candidate.stockCode())
                .judgementId(judgementId)
                .tradeDate(request.tradeDate())
                .watchDate(watchDate)
                .engineType(EngineType.LEADER)
                .targetType(TargetType.STOCK)
                .targetCode(candidate.stockCode())
                .targetName(candidate.stockName())
                .watchCode(watchCode)
                .title(title)
                .conditionExpression(condition)
                .expectedSignal(expectedSignal)
                .riskSignal(riskSignal)
                .priority(priority)
                .ruleVersion(request.ruleVersion())
                .validationStatus(WatchValidationStatus.PENDING)
                .build();
    }

    private List<CandidateEvaluation> rankEvaluations(List<CandidateEvaluation> evaluations) {
        List<CandidateEvaluation> sorted = evaluations.stream()
                .sorted(Comparator.comparing(CandidateEvaluation::competitionScore).reversed())
                .toList();
        List<CandidateEvaluation> ranked = new ArrayList<>();
        for (int i = 0; i < sorted.size(); i++) {
            CandidateEvaluation evaluation = sorted.get(i);
            ranked.add(new CandidateEvaluation(
                    evaluation.candidate(),
                    evaluation.factorResult(),
                    evaluation.competitionScore(),
                    i + 1,
                    samePlateRank(sorted, evaluation)
            ));
        }
        return ranked;
    }

    private int samePlateRank(List<CandidateEvaluation> sorted, CandidateEvaluation evaluation) {
        int rank = 1;
        for (CandidateEvaluation peer : sorted) {
            if (peer == evaluation) {
                return rank;
            }
            if (samePlate(peer.candidate(), evaluation.candidate())) {
                rank++;
            }
        }
        return rank;
    }

    private CandidateEvaluation samePlateLeader(List<CandidateEvaluation> rankedCandidates, CandidateEvaluation evaluation) {
        return rankedCandidates.stream()
                .filter(peer -> samePlate(peer.candidate(), evaluation.candidate()))
                .findFirst()
                .orElse(evaluation);
    }

    private boolean samePlate(CandidateIdentity left, CandidateIdentity right) {
        return StringUtils.hasText(left.plateCode()) && left.plateCode().equals(right.plateCode());
    }

    private BigDecimal competitionScore(List<FactorResult> factors) {
        BigDecimal weightedScore = score(factors);
        BigDecimal heightScore = cap(defaultZero(value(factors, "LEADER_CONSECUTIVE_LIMIT_DAYS"))
                .divide(new BigDecimal("7"), 4, RoundingMode.HALF_UP));
        BigDecimal position = defaultZero(value(factors, "LEADER_POSITION_SCORE"));
        BigDecimal drive = defaultZero(value(factors, "LEADER_DRIVE_SCORE"));
        BigDecimal popularity = defaultZero(value(factors, "LEADER_POPULARITY_SCORE"));
        BigDecimal repair = defaultZero(value(factors, "LEADER_DIVERGENCE_REPAIR"));
        BigDecimal challengeRisk = defaultZero(value(factors, "LEADER_CHALLENGE_RISK"));
        return weightedScore.multiply(new BigDecimal("0.4500"))
                .add(position.multiply(new BigDecimal("0.2200")))
                .add(heightScore.multiply(new BigDecimal("0.1300")))
                .add(drive.multiply(new BigDecimal("0.1000")))
                .add(popularity.multiply(new BigDecimal("0.0700")))
                .add(repair.multiply(new BigDecimal("0.0500")))
                .subtract(challengeRisk.multiply(new BigDecimal("0.0200")))
                .max(ZERO)
                .min(ONE)
                .setScale(4, RoundingMode.HALF_UP);
    }

    private int safeLimit(int candidateLimit) {
        if (candidateLimit <= 0) {
            return 5;
        }
        return Math.min(candidateLimit, 10);
    }

    private CandidateIdentity resolveCandidate(EngineRequest request) {
        List<CandidateIdentity> candidates = resolveCandidates(request, 1);
        if (!candidates.isEmpty()) {
            return candidates.get(0);
        }
        return CandidateIdentity.empty();
    }

    private List<CandidateIdentity> resolveCandidates(EngineRequest request, int candidateLimit) {
        if (StringUtils.hasText(request.targetCode())) {
            return List.of(resolveCandidateByStock(request, request.targetCode(), request.targetName()));
        }
        return safeRows(FactTable.STOCK_LIMIT_STATUS_DAILY, request).stream()
                .filter(row -> isLimitUp(row) || isBrokenLimit(row))
                .sorted(Comparator.comparing(this::candidateRawScore).reversed())
                .map(row -> resolveCandidateByStock(
                        request,
                        text(row.get("stock_code"), null),
                        text(row.get("stock_name"), null)
                ))
                .filter(candidate -> StringUtils.hasText(candidate.stockCode()))
                .collect(LinkedHashMap<String, CandidateIdentity>::new,
                        (map, candidate) -> map.putIfAbsent(candidate.stockCode(), candidate),
                        LinkedHashMap::putAll)
                .values()
                .stream()
                .limit(candidateLimit)
                .toList();
    }

    private CandidateIdentity resolveCandidateByStock(EngineRequest request, String stockCode, String stockNameFallback) {
        List<Map<String, Object>> limitRows = safeRows(FactTable.STOCK_LIMIT_STATUS_DAILY, request);
        Map<String, Object> limitRow = findByStock(limitRows, stockCode);
        String stockName = text(limitRow.get("stock_name"), stockNameFallback);
        Map<String, Object> relation = findBestRelation(request, stockCode, null);
        Map<String, Object> plate = findPlate(request, text(relation.get("plate_code"), stringParam(request, "plateCode", null)));
        return new CandidateIdentity(
                stockCode,
                stockName,
                text(relation.get("plate_code"), text(plate.get("plate_code"), stringParam(request, "plateCode", null))),
                text(relation.get("plate_name"), text(plate.get("plate_name"), stringParam(request, "plateName", null)))
        );
    }

    private CandidateIdentity resolveChallenger(List<CandidateEvaluation> rankedCandidates, CandidateEvaluation evaluation) {
        return rankedCandidates.stream()
                .filter(peer -> peer != evaluation)
                .filter(peer -> samePlate(peer.candidate(), evaluation.candidate()))
                .findFirst()
                .map(CandidateEvaluation::candidate)
                .orElseGet(() -> rankedCandidates.stream()
                        .filter(peer -> peer != evaluation)
                        .findFirst()
                        .map(CandidateEvaluation::candidate)
                        .orElse(CandidateIdentity.empty()));
    }

    private CandidateIdentity resolveChallenger(EngineRequest request, CandidateIdentity candidate) {
        if (!StringUtils.hasText(candidate.stockCode()) || !StringUtils.hasText(candidate.plateCode())) {
            return CandidateIdentity.empty();
        }
        return safeRows(FactTable.STOCK_PLATE_RELATION_SNAPSHOT, request).stream()
                .filter(row -> candidate.plateCode().equals(text(row.get("plate_code"), "")))
                .filter(row -> !candidate.stockCode().equals(text(row.get("stock_code"), "")))
                .map(row -> {
                    Map<String, Object> limit = findByStock(safeRows(FactTable.STOCK_LIMIT_STATUS_DAILY, request), text(row.get("stock_code"), ""));
                    return new ChallengerScore(
                            new CandidateIdentity(
                                    text(row.get("stock_code"), text(limit.get("stock_code"), null)),
                                    text(row.get("stock_name"), text(limit.get("stock_name"), null)),
                                    candidate.plateCode(),
                                    candidate.plateName()
                            ),
                            candidateRawScore(limit)
                    );
                })
                .filter(score -> StringUtils.hasText(score.candidate().stockCode()))
                .max(Comparator.comparing(ChallengerScore::score))
                .map(ChallengerScore::candidate)
                .orElse(CandidateIdentity.empty());
    }

    private EngineRequest withCandidate(EngineRequest request, CandidateIdentity candidate, Map<String, Object> extraParams) {
        return new EngineRequest(
                request.tradeDate(),
                request.asOfDate(),
                request.judgementMode(),
                EngineType.LEADER,
                TargetType.STOCK,
                candidate.stockCode(),
                candidate.stockName(),
                request.ruleVersion(),
                request.ruleContext(),
                request.dataQualityContext(),
                mergeParams(request.params(), candidate, extraParams)
        );
    }

    private Map<String, Object> mergeParams(Map<String, Object> params, CandidateIdentity candidate) {
        return mergeParams(params, candidate, Map.of());
    }

    private Map<String, Object> mergeParams(Map<String, Object> params, CandidateIdentity candidate, Map<String, Object> extraParams) {
        HashMap<String, Object> merged = new HashMap<>();
        if (params != null) {
            merged.putAll(params);
        }
        if (extraParams != null) {
            merged.putAll(extraParams);
        }
        if (StringUtils.hasText(candidate.plateCode())) {
            merged.put("plateCode", candidate.plateCode());
        }
        if (StringUtils.hasText(candidate.plateName())) {
            merged.put("plateName", candidate.plateName());
        }
        return merged;
    }

    private String buildReason(LeaderType type, LeaderStatus status, CandidateEvaluation evaluation,
                               CandidateEvaluation marketLeader, BigDecimal position, BigDecimal height,
                               BigDecimal drive, BigDecimal popularity, BigDecimal repair) {
        String leaderName = defaultText(marketLeader.candidate().stockName(), defaultText(marketLeader.candidate().stockCode(), "暂无"));
        BigDecimal leaderGap = marketLeader.competitionScore().subtract(evaluation.competitionScore()).max(ZERO);
        return "类型=" + type + "，状态=" + status + "；地位分" + position
                + "、高度" + height.stripTrailingZeros().toPlainString()
                + "、带动性" + drive + "、人气" + popularity + "、分歧修复" + repair
                + "，全市场排序第" + evaluation.marketRank()
                + "、同板块排序第" + evaluation.samePlateRank()
                + "，与当前第一候选" + leaderName + "竞争分差" + leaderGap
                + "，共同决定当前是上位、被挑战还是跟风。";
    }

    private String buildRisk(LeaderStatus status, BigDecimal challengeRisk, BigDecimal repair, BigDecimal drive) {
        if (status == LeaderStatus.CHALLENGED) {
            return "被挑战风险较高，若明日挑战者继续晋级而当前候选不能修复，容易发生卡位。";
        }
        if (status == LeaderStatus.DROPPED || status == LeaderStatus.DEAD) {
            return "地位或修复能力已经明显走弱，风险模型优先按退潮或掉队处理。";
        }
        if (challengeRisk.compareTo(new BigDecimal("0.4500")) >= 0) {
            return "挑战压力开始抬升，需要防止一致后兑现或同板块切换。";
        }
        if (repair.compareTo(new BigDecimal("0.5000")) < 0 || drive.compareTo(new BigDecimal("0.4500")) < 0) {
            return "分歧修复或带动性尚未充分验证，不能只因上涨强度给出确认。";
        }
        return "挑战压力可控，核心风险在于明日能否继续带动主线并经受分歧检验。";
    }

    private String buildTomorrowValidation(LeaderType type, LeaderStatus status) {
        if (status == LeaderStatus.CHALLENGED) {
            return "明日验证当前候选能否先于挑战者表态，是否高开强承接、分歧回封，并压住同板块卡位。";
        }
        if (status == LeaderStatus.DROPPED || status == LeaderStatus.DEAD) {
            return "明日验证是否出现超预期修复；若继续低开、断板无承接或板块不跟随，确认掉队。";
        }
        if (type == LeaderType.FOLLOWER || type == LeaderType.MIDDLE_ARMY) {
            return "明日验证其上涨是否能反哺板块；若只跟随龙头脉冲而不能带队，继续按跟风或中军处理。";
        }
        return "明日验证是否继续晋级或分歧转一致，板块梯队、人气和中军承接是否同步跟随。";
    }

    private String buildConclusion(CandidateIdentity candidate, LeaderType type, LeaderStatus status, String reason, String risk) {
        String name = defaultText(candidate.stockName(), defaultText(candidate.stockCode(), "自动候选"));
        return name + "判断为" + type + "/" + status + "；" + reason + " 风险：" + risk;
    }

    private void collect(boolean passed, String satisfiedText, String unmetText, List<String> satisfied, List<String> unmet) {
        if (passed) {
            satisfied.add(satisfiedText);
        } else {
            unmet.add(unmetText);
        }
    }

    private BigDecimal value(List<FactorResult> factors, String factorCode) {
        return factors == null ? null : factors.stream()
                .filter(factor -> factorCode.equals(factor.getFactorCode()))
                .findFirst()
                .map(FactorResult::getFactorValue)
                .orElse(null);
    }

    private BigDecimal score(List<FactorResult> factors) {
        BigDecimal totalWeight = ZERO;
        BigDecimal weightedScore = ZERO;
        if (factors == null) {
            return ZERO;
        }
        for (FactorResult factor : factors) {
            BigDecimal weight = factor.getWeight() == null ? ZERO : factor.getWeight();
            BigDecimal score = factor.getScore() == null ? ZERO : factor.getScore();
            totalWeight = totalWeight.add(weight);
            weightedScore = weightedScore.add(score.multiply(weight));
        }
        if (totalWeight.compareTo(BigDecimal.ZERO) <= 0) {
            return ZERO;
        }
        return weightedScore.divide(totalWeight, 4, RoundingMode.HALF_UP);
    }

    private int countEvidence(List<FactorResult> factors, EvidenceType evidenceType) {
        if (factors == null) {
            return 0;
        }
        return (int) factors.stream()
                .filter(factor -> factor.getEvidenceType() == evidenceType)
                .count();
    }

    private BigDecimal confidence(BigDecimal score, int conflictCount, int warningCount, String stockCode) {
        BigDecimal penalty = BigDecimal.valueOf(conflictCount).multiply(new BigDecimal("0.0500"))
                .add(BigDecimal.valueOf(warningCount).multiply(new BigDecimal("0.0300")));
        if (!StringUtils.hasText(stockCode)) {
            penalty = penalty.add(new BigDecimal("0.1500"));
        }
        BigDecimal confidence = score.multiply(new BigDecimal("0.7000"))
                .add(new BigDecimal("0.3000"))
                .subtract(penalty);
        if (confidence.compareTo(ZERO) < 0) {
            return ZERO;
        }
        if (confidence.compareTo(ONE) > 0) {
            return ONE;
        }
        return confidence.setScale(4, RoundingMode.HALF_UP);
    }

    private BigDecimal candidateRawScore(Map<String, Object> limitRow) {
        BigDecimal height = defaultZero(rowDecimal(limitRow, "consecutive_limit_up_days")).multiply(new BigDecimal("10"));
        BigDecimal seal = cap(defaultZero(rowDecimal(limitRow, "seal_amount")).divide(new BigDecimal("1000000000"), 4, RoundingMode.HALF_UP))
                .multiply(new BigDecimal("8"));
        BigDecimal status = isLimitUp(limitRow) ? new BigDecimal("20") : isBrokenLimit(limitRow) ? new BigDecimal("6") : ZERO;
        BigDecimal openPenalty = defaultZero(rowDecimal(limitRow, "open_limit_times")).multiply(new BigDecimal("2"));
        return height.add(seal).add(status).subtract(openPenalty);
    }

    private Map<String, Object> findBestRelation(EngineRequest request, String stockCode, String plateCode) {
        if (!StringUtils.hasText(stockCode)) {
            return Map.of();
        }
        return safeRows(FactTable.STOCK_PLATE_RELATION_SNAPSHOT, request).stream()
                .filter(row -> stockCode.equals(text(row.get("stock_code"), "")))
                .filter(row -> !StringUtils.hasText(plateCode) || plateCode.equals(text(row.get("plate_code"), "")))
                .max(Comparator.comparing(row -> defaultZero(rowDecimal(row, "relation_confidence"))))
                .orElse(Map.of());
    }

    private Map<String, Object> findPlate(EngineRequest request, String plateCode) {
        if (!StringUtils.hasText(plateCode)) {
            return Map.of();
        }
        return safeRows(FactTable.PLATE_DAILY_SNAPSHOT, request).stream()
                .filter(row -> plateCode.equals(text(row.get("plate_code"), "")))
                .findFirst()
                .orElse(Map.of());
    }

    private Map<String, Object> findByStock(List<Map<String, Object>> rows, String stockCode) {
        if (!StringUtils.hasText(stockCode)) {
            return Map.of();
        }
        return rows.stream()
                .filter(row -> stockCode.equals(text(row.get("stock_code"), "")))
                .findFirst()
                .orElse(Map.of());
    }

    private List<Map<String, Object>> safeRows(FactTable table, EngineRequest request) {
        List<Map<String, Object>> rows = marketFactService.findByTradeDate(table, request.tradeDate());
        return rows == null ? List.of() : rows;
    }

    private boolean isLimitUp(Map<String, Object> row) {
        return "LIMIT_UP".equals(normalize(row.get("limit_status")));
    }

    private boolean isBrokenLimit(Map<String, Object> row) {
        String status = normalize(row.get("limit_status"));
        return "BROKEN_LIMIT".equals(status) || "OPEN_LIMIT".equals(status);
    }

    private BigDecimal rowDecimal(Map<String, Object> row, String key) {
        if (row == null || row.get(key) == null) {
            return null;
        }
        Object value = row.get(key);
        if (value instanceof BigDecimal decimal) {
            return decimal;
        }
        if (value instanceof Number number) {
            return BigDecimal.valueOf(number.doubleValue());
        }
        String text = Objects.toString(value, "").trim();
        if (text.isEmpty()) {
            return null;
        }
        return new BigDecimal(text);
    }

    private BigDecimal defaultZero(BigDecimal value) {
        return value == null ? ZERO : value;
    }

    private BigDecimal cap(BigDecimal value) {
        if (value == null) {
            return ZERO;
        }
        return value.max(ZERO).min(ONE).setScale(4, RoundingMode.HALF_UP);
    }

    private String normalize(Object value) {
        return Objects.toString(value, "").trim().toUpperCase(Locale.ROOT);
    }

    private String text(Object value, String fallback) {
        String text = Objects.toString(value, "").trim();
        return text.isEmpty() ? fallback : text;
    }

    private String defaultText(String value, String defaultValue) {
        return StringUtils.hasText(value) ? value : defaultValue;
    }

    private String stringParam(EngineRequest request, String key, String defaultValue) {
        if (request.params() == null || request.params().get(key) == null) {
            return defaultValue;
        }
        return String.valueOf(request.params().get(key));
    }

    private JudgementMode defaultMode(JudgementMode judgementMode) {
        return judgementMode == null ? JudgementMode.REALTIME : judgementMode;
    }

    private record CandidateIdentity(
            String stockCode,
            String stockName,
            String plateCode,
            String plateName
    ) {
        private static CandidateIdentity empty() {
            return new CandidateIdentity(null, null, null, null);
        }
    }

    private record ChallengerScore(
            CandidateIdentity candidate,
            BigDecimal score
    ) {
    }

    private record CandidateEvaluation(
            CandidateIdentity candidate,
            FactorCalculateResult factorResult,
            BigDecimal competitionScore,
            int marketRank,
            int samePlateRank
    ) {
    }

    private record LeaderDecision(
            LeaderType leaderType,
            LeaderStatus leaderStatus,
            BigDecimal confidence,
            String conclusion,
            List<String> satisfiedConditions,
            String unmetCondition,
            String tomorrowValidation,
            String leaderReason,
            String leaderRisk
    ) {
    }
}
