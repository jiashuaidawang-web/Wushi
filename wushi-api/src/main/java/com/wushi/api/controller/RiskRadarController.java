package com.wushi.api.controller;

import com.wushi.api.vo.page.RiskRadarVO;
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
@RequestMapping("/api/risk")
public class RiskRadarController {

    @GetMapping("/radar")
    public ApiResponse<RiskRadarVO> radar(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate tradeDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate asOfDate,
            @RequestParam(required = false) JudgementMode judgementMode,
            @RequestParam(required = false) String ruleVersion) {
        return ApiResponse.ok(new RiskRadarVO(
                ApiQuerySupport.query(tradeDate, asOfDate, judgementMode, ruleVersion),
                List.of(),
                "风险雷达接口骨架已就绪"
        ));
    }
}
