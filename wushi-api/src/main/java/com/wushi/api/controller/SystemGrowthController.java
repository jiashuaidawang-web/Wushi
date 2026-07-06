package com.wushi.api.controller;

import com.wushi.api.vo.page.SystemGrowthVO;
import com.wushi.common.api.ApiResponse;
import com.wushi.common.enums.JudgementMode;
import com.wushi.module.backtest.service.SystemGrowthQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/system")
@RequiredArgsConstructor
public class SystemGrowthController {

    private final SystemGrowthQueryService systemGrowthQueryService;

    @GetMapping("/growth")
    public ApiResponse<SystemGrowthVO> growth(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate tradeDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate asOfDate,
            @RequestParam(required = false) JudgementMode judgementMode,
            @RequestParam(required = false) String ruleVersion) {
        var query = ApiQuerySupport.query(tradeDate, asOfDate, judgementMode, ruleVersion);
        return ApiResponse.ok(new SystemGrowthVO(
                query,
                systemGrowthQueryService.factorResults(query.tradeDate(), query.ruleVersion()),
                systemGrowthQueryService.combinationResults(query.tradeDate(), query.ruleVersion()),
                systemGrowthQueryService.growthLogs(query.tradeDate())
        ));
    }
}
