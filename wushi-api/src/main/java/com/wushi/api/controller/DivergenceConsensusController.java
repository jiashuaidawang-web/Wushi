package com.wushi.api.controller;

import com.wushi.api.assembler.JudgmentBlockAssembler;
import com.wushi.api.vo.common.JudgmentBlockVO;
import com.wushi.api.vo.common.MarketQuery;
import com.wushi.api.vo.page.DivergenceConsensusVO;
import com.wushi.common.api.ApiResponse;
import com.wushi.common.enums.DataQualityLevel;
import com.wushi.common.enums.EngineType;
import com.wushi.common.enums.JudgementMode;
import com.wushi.common.enums.TargetType;
import com.wushi.module.market.common.DataCoverageChecker;
import com.wushi.module.market.enums.FactTable;
import com.wushi.module.pattern.engine.DivergenceConsensusEngine;
import com.wushi.module.pattern.model.DivergenceConsensusDetail;
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

@RestController
@RequestMapping("/api/divergence")
@RequiredArgsConstructor
public class DivergenceConsensusController {

    private static final List<String> REQUIRED_TABLES = List.of(
            "stock_limit_status_daily",
            "stock_limit_intraday_event",
            "stock_daily_kline",
            "plate_daily_snapshot",
            "stock_plate_relation_snapshot",
            "high_position_feedback_daily"
    );
    private static final FactTable PRIMARY_TABLE = FactTable.STOCK_LIMIT_STATUS_DAILY;

    private final EngineRequestFactory engineRequestFactory;
    private final DivergenceConsensusEngine divergenceConsensusEngine;
    private final JudgmentBlockAssembler judgmentBlockAssembler;
    private final DataCoverageChecker dataCoverageChecker;

    @GetMapping("/dashboard")
    public ApiResponse<DivergenceConsensusVO> dashboard(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate tradeDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate asOfDate,
            @RequestParam(required = false) JudgementMode judgementMode,
            @RequestParam(required = false) String ruleVersion,
            @RequestParam(required = false) String plateCode,
            @RequestParam(required = false) String plateName) {
        MarketQuery query = ApiQuerySupport.query(tradeDate, asOfDate, judgementMode, ruleVersion);

        // 数据覆盖率检查
        DataQualityLevel coverageLevel = dataCoverageChecker.checkLevel(PRIMARY_TABLE, query.tradeDate());
        if (coverageLevel == DataQualityLevel.LOW) {
            return ApiResponse.ok(new DivergenceConsensusVO(query, List.of(),
                    "数据不足，引擎结果仅供参考"));
        }

        EngineRequest request = engineRequestFactory.create(
                query.tradeDate(),
                query.asOfDate(),
                query.judgementMode(),
                EngineType.DIVERGENCE_CONSENSUS,
                plateCode == null || plateCode.isBlank() ? TargetType.MARKET : TargetType.PLATE,
                plateCode == null || plateCode.isBlank() ? "MARKET" : plateCode,
                plateCode == null || plateCode.isBlank() ? "全市场" : plateName,
                query.ruleVersion(),
                REQUIRED_TABLES,
                requestParams(plateCode, plateName)
        );
        var judgement = divergenceConsensusEngine.judge(request);
        // L2 降级标记
        if (coverageLevel == DataQualityLevel.MEDIUM && judgement != null) {
            judgement.setDataQualityLevel(DataQualityLevel.MEDIUM);
        }
        JudgmentBlockVO<DivergenceConsensusDetail> block =
                judgmentBlockAssembler.toBlock(judgement);
        return ApiResponse.ok(new DivergenceConsensusVO(query, List.of(block), buildSummary(block)));
    }

    private Map<String, Object> requestParams(String plateCode, String plateName) {
        java.util.HashMap<String, Object> params = new java.util.HashMap<>();
        if (plateCode != null && !plateCode.isBlank()) {
            params.put("plateCode", plateCode);
        }
        if (plateName != null && !plateName.isBlank()) {
            params.put("plateName", plateName);
        }
        return params;
    }

    private String buildSummary(JudgmentBlockVO<DivergenceConsensusDetail> judgement) {
        if (judgement == null || judgement.detail() == null) {
            return "分歧一致推演暂不可用";
        }
        return judgement.detail().targetName() + "处于" + judgement.detail().state()
                + "，明日验证：" + judgement.detail().tomorrowValidation();
    }
}
