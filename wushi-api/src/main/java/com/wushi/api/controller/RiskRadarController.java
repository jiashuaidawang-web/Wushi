package com.wushi.api.controller;

import com.wushi.api.assembler.JudgmentBlockAssembler;
import com.wushi.api.vo.common.JudgmentBlockVO;
import com.wushi.api.vo.common.MarketQuery;
import com.wushi.api.vo.page.RiskRadarVO;
import com.wushi.common.api.ApiResponse;
import com.wushi.common.enums.EngineType;
import com.wushi.common.enums.JudgementMode;
import com.wushi.common.enums.TargetType;
import com.wushi.module.risk.engine.RiskRadarEngine;
import com.wushi.module.risk.model.RiskRadarDetail;
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
@RequestMapping("/api/risk")
@RequiredArgsConstructor
public class RiskRadarController {

    private static final List<String> REQUIRED_TABLES = List.of(
            "high_position_feedback_daily",
            "stock_limit_status_daily",
            "market_breadth_daily_snapshot",
            "plate_daily_snapshot"
    );

    private final EngineRequestFactory engineRequestFactory;
    private final RiskRadarEngine riskRadarEngine;
    private final JudgmentBlockAssembler judgmentBlockAssembler;

    @GetMapping("/radar")
    public ApiResponse<RiskRadarVO> radar(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate tradeDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate asOfDate,
            @RequestParam(required = false) JudgementMode judgementMode,
            @RequestParam(required = false) String ruleVersion,
            @RequestParam(required = false) String plateCode,
            @RequestParam(required = false) String plateName) {
        MarketQuery query = ApiQuerySupport.query(tradeDate, asOfDate, judgementMode, ruleVersion);
        EngineRequest request = engineRequestFactory.create(
                query.tradeDate(),
                query.asOfDate(),
                query.judgementMode(),
                EngineType.RISK,
                plateCode == null || plateCode.isBlank() ? TargetType.MARKET : TargetType.PLATE,
                plateCode == null || plateCode.isBlank() ? "MARKET" : plateCode,
                plateCode == null || plateCode.isBlank() ? "全市场" : plateName,
                query.ruleVersion(),
                REQUIRED_TABLES,
                plateCode == null || plateCode.isBlank() ? Map.of() : Map.of("plateCode", plateCode)
        );
        JudgmentBlockVO<RiskRadarDetail> riskCard = judgmentBlockAssembler.toBlock(riskRadarEngine.judge(request));
        return ApiResponse.ok(new RiskRadarVO(query, List.of(riskCard), buildSummary(riskCard)));
    }

    private String buildSummary(JudgmentBlockVO<RiskRadarDetail> riskCard) {
        if (riskCard == null || riskCard.detail() == null) {
            return "风险雷达暂不可用";
        }
        return riskCard.detail().riskLevel() + "/" + riskCard.detail().riskType()
                + "，降风险验证：" + riskCard.detail().reduceRiskSignal();
    }
}
