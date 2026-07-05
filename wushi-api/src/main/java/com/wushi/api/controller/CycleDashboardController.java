package com.wushi.api.controller;

import com.wushi.api.vo.page.CycleDashboardVO;
import com.wushi.common.api.ApiResponse;
import com.wushi.common.enums.JudgementMode;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/cycle")
public class CycleDashboardController {

    @GetMapping("/dashboard")
    public ApiResponse<CycleDashboardVO> dashboard(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate tradeDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate asOfDate,
            @RequestParam(required = false) JudgementMode judgementMode,
            @RequestParam(required = false) String ruleVersion) {
        return ApiResponse.ok(new CycleDashboardVO(
                ApiQuerySupport.query(tradeDate, asOfDate, judgementMode, ruleVersion),
                null,
                "周期驾驶舱接口骨架已就绪"
        ));
    }
}
