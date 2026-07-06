package com.wushi.api.controller;

import com.wushi.api.vo.page.ReviewCorrectionVO;
import com.wushi.common.api.ApiResponse;
import com.wushi.common.enums.JudgementMode;
import com.wushi.module.review.model.*;
import com.wushi.module.review.service.EvidenceLabelService;
import com.wushi.module.review.service.HistoricalSampleConfirmationService;
import com.wushi.module.review.service.ManualCorrectionService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/review")
@RequiredArgsConstructor
public class ReviewCorrectionController {

    private final ManualCorrectionService manualCorrectionService;
    private final EvidenceLabelService evidenceLabelService;
    private final HistoricalSampleConfirmationService historicalSampleConfirmationService;

    @GetMapping("/correction")
    public ApiResponse<ReviewCorrectionVO> correction(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate tradeDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate asOfDate,
            @RequestParam(required = false) JudgementMode judgementMode,
            @RequestParam(required = false) String ruleVersion) {
        return ApiResponse.ok(new ReviewCorrectionVO(
                ApiQuerySupport.query(tradeDate, asOfDate, judgementMode, ruleVersion),
                List.of("CYCLE", "MAINLINE", "LEADER", "DIVERGENCE_CONSENSUS", "RISK"),
                List.of("结论偏差", "证据权重偏差", "冲突证据走出", "历史样本标签确认"),
                "复盘修正台已接入人工修正、证据标注、历史样本确认，修正会进入系统成长日志"
        ));
    }

    @PostMapping("/correction")
    public ApiResponse<ManualCorrectionResult> correct(@RequestBody ManualCorrectionRequest request) {
        return ApiResponse.ok(manualCorrectionService.correct(request));
    }

    @PostMapping("/correction/{correctionId}/revoke")
    public ApiResponse<ManualCorrectionResult> revoke(@PathVariable String correctionId,
                                                      @RequestParam(required = false) String reviewer,
                                                      @RequestParam(required = false) String reason) {
        return ApiResponse.ok(manualCorrectionService.revoke(correctionId, reviewer, reason));
    }

    @PostMapping("/evidence-label")
    public ApiResponse<EvidenceLabelResultInfo> labelEvidence(@RequestBody EvidenceLabelRequest request) {
        return ApiResponse.ok(evidenceLabelService.label(request));
    }

    @PostMapping("/sample-confirmation")
    public ApiResponse<HistoricalSampleConfirmationResult> confirmSample(@RequestBody HistoricalSampleConfirmationRequest request) {
        return ApiResponse.ok(historicalSampleConfirmationService.confirm(request));
    }
}
