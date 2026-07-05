package com.wushi.api.controller;

import com.wushi.api.vo.page.MarketOverviewVO;
import com.wushi.common.api.ApiResponse;
import com.wushi.common.enums.JudgementMode;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/market")
public class MarketOverviewController {

    @GetMapping("/overview")
    public ApiResponse<MarketOverviewVO> overview(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate tradeDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate asOfDate,
            @RequestParam(required = false) JudgementMode judgementMode,
            @RequestParam(required = false) String ruleVersion) {
        return ApiResponse.ok(new MarketOverviewVO(
                ApiQuerySupport.query(tradeDate, asOfDate, judgementMode, ruleVersion),
                null,
                List.of(),
                List.of(),
                null,
                null,
                List.of(),
                List.of()
        ));
    }
}
