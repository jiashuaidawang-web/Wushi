package com.wushi.api.controller;

import com.wushi.api.assembler.JudgmentBlockAssembler;
import com.wushi.api.vo.common.JudgmentBlockVO;
import com.wushi.api.vo.common.MarketQuery;
import com.wushi.api.vo.page.MainlineDashboardVO;
import com.wushi.common.api.ApiResponse;
import com.wushi.common.enums.DataQualityLevel;
import com.wushi.common.enums.EngineType;
import com.wushi.common.enums.JudgementMode;
import com.wushi.common.enums.TargetType;
import com.wushi.module.market.common.DataCoverageChecker;
import com.wushi.common.model.JudgementResult;
import com.wushi.module.mainline.engine.MainlineRecognitionEngine;
import com.wushi.module.mainline.model.MainlineCandidate;
import com.wushi.module.mainline.model.MainlineJudgementDetail;
import com.wushi.module.mainline.service.MainlineCandidateSelector;
import com.wushi.module.market.enums.FactTable;
import com.wushi.module.rule.engine.core.EngineRequest;
import com.wushi.module.rule.support.EngineRequestFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/mainline")
@RequiredArgsConstructor
public class MainlineDashboardController {

    private static final List<String> REQUIRED_TABLES = List.of(
            "plate_daily_snapshot",
            "capital_flow_daily_snapshot"
    );
    private static final FactTable PRIMARY_TABLE = FactTable.PLATE_DAILY_SNAPSHOT;

    private final EngineRequestFactory engineRequestFactory;
    private final MainlineRecognitionEngine mainlineRecognitionEngine;
    private final MainlineCandidateSelector mainlineCandidateSelector;
    private final JudgmentBlockAssembler judgmentBlockAssembler;
    private final DataCoverageChecker dataCoverageChecker;

    @GetMapping("/dashboard")
    public ApiResponse<MainlineDashboardVO> dashboard(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate tradeDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate asOfDate,
            @RequestParam(required = false) JudgementMode judgementMode,
            @RequestParam(required = false) String ruleVersion,
            @RequestParam(required = false) String plateCode,
            @RequestParam(required = false) String plateName,
            @RequestParam(required = false, defaultValue = "5") Integer candidateLimit) {
        try {
            MarketQuery query = ApiQuerySupport.query(tradeDate, asOfDate, judgementMode, ruleVersion);

            // 数据覆盖率检查
            DataQualityLevel coverageLevel = dataCoverageChecker.checkLevel(PRIMARY_TABLE, query.tradeDate());
            if (coverageLevel == DataQualityLevel.LOW) {
                return ApiResponse.ok(new MainlineDashboardVO(query, List.of(), "数据不足，引擎结果仅供参考"));
            }

            List<JudgmentBlockVO<MainlineJudgementDetail>> mainlineCards = buildMainlineCards(
                    query, plateCode, plateName, candidateLimit, coverageLevel);
            String summary = buildCompetitionSummary(mainlineCards);
            return ApiResponse.ok(new MainlineDashboardVO(query, mainlineCards, summary));
        } catch (Exception ex) {
            MarketQuery query = ApiQuerySupport.query(tradeDate, asOfDate, judgementMode, ruleVersion);
            return ApiResponse.ok(new MainlineDashboardVO(query, List.of(), "主线引擎暂不可用：" + ex.getMessage()));
        }
    }

    private List<JudgmentBlockVO<MainlineJudgementDetail>> buildMainlineCards(
            MarketQuery query,
            String plateCode,
            String plateName,
            Integer candidateLimit,
            DataQualityLevel coverageLevel) {
        if (plateCode != null && !plateCode.isBlank()) {
            EngineRequest request = createRequest(query, plateCode, plateName, Map.of());
            JudgementResult<MainlineJudgementDetail> judgement = mainlineRecognitionEngine.judge(request);
            if (judgement == null) {
                judgement = emptyResult(query);
            }
            applyCoverageLevel(judgement, coverageLevel);
            return List.of(judgmentBlockAssembler.toBlock(judgement));
        }

        List<MainlineCandidate> candidates = mainlineCandidateSelector.selectCandidates(query.tradeDate(), safeLimit(candidateLimit));
        if (candidates.isEmpty()) {
            EngineRequest request = createRequest(query, null, null, Map.of());
            JudgementResult<MainlineJudgementDetail> judgement = mainlineRecognitionEngine.judge(request);
            if (judgement == null) {
                judgement = emptyResult(query);
            }
            applyCoverageLevel(judgement, coverageLevel);
            return List.of(judgmentBlockAssembler.toBlock(judgement));
        }

        return candidates.stream()
                .map(candidate -> {
                    EngineRequest request = createRequest(query, candidate.plateCode(), candidate.plateName(), candidateParams(candidate));
                    JudgementResult<MainlineJudgementDetail> judgement = mainlineRecognitionEngine.judge(request);
                    if (judgement == null) {
                        judgement = emptyResult(query);
                    }
                    applyCoverageLevel(judgement, coverageLevel);
                    return judgmentBlockAssembler.toBlock(judgement);
                })
                .toList();
    }

    /**
     * L2 降级: 覆盖率不足时覆盖 JudgementResult 的 dataQualityLevel
     */
    private void applyCoverageLevel(JudgementResult<MainlineJudgementDetail> judgement, DataQualityLevel coverageLevel) {
        if (coverageLevel == DataQualityLevel.MEDIUM) {
            judgement.setDataQualityLevel(DataQualityLevel.MEDIUM);
        }
    }

    private EngineRequest createRequest(MarketQuery query, String plateCode, String plateName, Map<String, Object> params) {
        return engineRequestFactory.create(
                query.tradeDate(),
                query.asOfDate(),
                query.judgementMode(),
                EngineType.MAINLINE,
                TargetType.PLATE,
                plateCode,
                plateName,
                query.ruleVersion(),
                REQUIRED_TABLES,
                params
        );
    }

    private Map<String, Object> candidateParams(MainlineCandidate candidate) {
        return Map.of(
                "plateType", candidate.plateType(),
                "candidateRank", candidate.candidateRank(),
                "candidateScore", candidate.candidateScore(),
                "candidateReason", candidate.candidateReason()
        );
    }

    private int safeLimit(Integer candidateLimit) {
        if (candidateLimit == null || candidateLimit <= 0) {
            return 5;
        }
        return Math.min(candidateLimit, 10);
    }

    private String buildCompetitionSummary(List<JudgmentBlockVO<MainlineJudgementDetail>> mainlineCards) {
        if (mainlineCards == null || mainlineCards.isEmpty()) {
            return "主线推演暂不可用";
        }
        JudgmentBlockVO<MainlineJudgementDetail> first = mainlineCards.get(0);
        if (first.detail() == null) {
            return "主线推演暂不可用";
        }
        String leader = first.detail().plateName() == null ? first.detail().plateCode() : first.detail().plateName();
        return "当前排名第一的主线候选是 " + leader + "，"
                + first.detail().candidateReason() + " 明日重点验证：" + first.detail().tomorrowValidation();
    }

    private JudgementResult<MainlineJudgementDetail> emptyResult(MarketQuery query) {
        return JudgementResult.<MainlineJudgementDetail>builder()
                .judgementId("MAINLINE-EMPTY-" + UUID.randomUUID())
                .tradeDate(query.tradeDate())
                .asOfDate(query.asOfDate())
                .judgementMode(query.judgementMode())
                .engineType(EngineType.MAINLINE)
                .targetType(TargetType.PLATE)
                .conclusion("暂无数据")
                .ruleVersion(query.ruleVersion())
                .build();
    }
}
