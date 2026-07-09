package com.wushi.api.controller;

import com.wushi.api.vo.page.HistoricalSimilarityVO;
import com.wushi.common.api.ApiResponse;
import com.wushi.common.enums.DataQualityLevel;
import com.wushi.common.enums.EngineType;
import com.wushi.common.enums.JudgementMode;
import com.wushi.common.enums.TargetType;
import com.wushi.module.market.common.DataCoverageChecker;
import com.wushi.module.market.enums.FactTable;
import com.wushi.module.rule.engine.core.EngineRequest;
import com.wushi.module.rule.support.EngineRequestFactory;
import com.wushi.module.similarity.engine.HistoricalSimilarityEngine;
import com.wushi.module.similarity.model.HistoricalSimilarityMatch;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/history")
@RequiredArgsConstructor
public class HistoricalSimilarityController {

    private static final FactTable PRIMARY_TABLE = FactTable.STOCK_DAILY_KLINE;

    private final EngineRequestFactory engineRequestFactory;
    private final HistoricalSimilarityEngine historicalSimilarityEngine;
    private final DataCoverageChecker dataCoverageChecker;

    @GetMapping("/similarity")
    public ApiResponse<HistoricalSimilarityVO> similarity(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate tradeDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate asOfDate,
            @RequestParam(required = false) JudgementMode judgementMode,
            @RequestParam(required = false) String ruleVersion,
            @RequestParam(required = false) EngineType engineType,
            @RequestParam(required = false) TargetType targetType,
            @RequestParam(required = false) String targetCode,
            @RequestParam(required = false) String targetName,
            @RequestParam(required = false, defaultValue = "10") Integer limit) {
        try {
            var query = ApiQuerySupport.query(tradeDate, asOfDate, judgementMode, ruleVersion);

            // 数据覆盖率检查
            DataQualityLevel coverageLevel = dataCoverageChecker.checkLevel(PRIMARY_TABLE, query.tradeDate());
            if (coverageLevel == DataQualityLevel.LOW) {
                return ApiResponse.ok(new HistoricalSimilarityVO(
                        query, List.of(), "数据不足，引擎结果仅供参考"));
            }

            EngineRequest request = engineRequestFactory.create(
                    query.tradeDate(),
                    query.asOfDate(),
                    query.judgementMode(),
                    engineType == null ? EngineType.MARKET_OVERVIEW : engineType,
                    targetType == null ? TargetType.MARKET : targetType,
                    targetCode,
                    targetName,
                    query.ruleVersion(),
                    java.util.List.of("judgement_evidence_item", "historical_similarity_match"),
                    Map.of("limit", Math.min(Math.max(limit == null ? 10 : limit, 1), 50))
            );
            var matches = historicalSimilarityEngine.match(request);
            if (matches == null) {
                matches = List.of();
            }

            String summary = matches.isEmpty()
                    ? "未找到足够相似样本，等待更多判断证据和后验表现沉淀"
                    : "已按周期/主线/龙头/分歧/风险结构匹配历史样本";

            // L2 降级时追加提示
            if (coverageLevel == DataQualityLevel.MEDIUM) {
                summary = "[数据不足，引擎结果仅供参考] " + summary;
            }

            return ApiResponse.ok(new HistoricalSimilarityVO(query, matches, summary));
        } catch (Exception ex) {
            var query = ApiQuerySupport.query(tradeDate, asOfDate, judgementMode, ruleVersion);
            return ApiResponse.ok(new HistoricalSimilarityVO(
                    query, List.of(), "历史相似引擎暂不可用：" + ex.getMessage()));
        }
    }
}
