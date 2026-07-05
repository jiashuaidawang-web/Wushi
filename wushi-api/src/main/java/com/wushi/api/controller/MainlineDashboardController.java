package com.wushi.api.controller;

import com.wushi.api.assembler.JudgmentBlockAssembler;
import com.wushi.api.vo.common.MarketQuery;
import com.wushi.api.vo.page.MainlineDashboardVO;
import com.wushi.common.api.ApiResponse;
import com.wushi.common.enums.EngineType;
import com.wushi.common.enums.JudgementMode;
import com.wushi.common.enums.TargetType;
import com.wushi.module.mainline.engine.MainlineRecognitionEngine;
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
@RequestMapping("/api/mainline")
@RequiredArgsConstructor
public class MainlineDashboardController {

    private static final List<String> REQUIRED_TABLES = List.of(
            "plate_daily_snapshot",
            "capital_flow_daily_snapshot"
    );

    private final EngineRequestFactory engineRequestFactory;
    private final MainlineRecognitionEngine mainlineRecognitionEngine;
    private final JudgmentBlockAssembler judgmentBlockAssembler;

    @GetMapping("/dashboard")
    public ApiResponse<MainlineDashboardVO> dashboard(
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
                EngineType.MAINLINE,
                TargetType.PLATE,
                plateCode,
                plateName,
                query.ruleVersion(),
                REQUIRED_TABLES,
                Map.of()
        );
        var judgement = mainlineRecognitionEngine.judge(request);
        String summary = judgement.getDetail() == null
                ? "主线推演暂不可用"
                : judgement.getDetail().tomorrowValidation();
        return ApiResponse.ok(new MainlineDashboardVO(
                query,
                List.of(judgmentBlockAssembler.toBlock(judgement)),
                summary
        ));
    }
}
