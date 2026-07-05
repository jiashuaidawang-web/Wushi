package com.wushi.api.controller;

import com.wushi.api.vo.page.ReviewCorrectionVO;
import com.wushi.common.api.ApiResponse;
import com.wushi.common.enums.JudgementMode;
import com.wushi.module.review.model.*;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/review")
public class ReviewCorrectionController {

    @GetMapping("/correction")
    public ApiResponse<ReviewCorrectionVO> correction(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate tradeDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate asOfDate,
            @RequestParam(required = false) JudgementMode judgementMode,
            @RequestParam(required = false) String ruleVersion) {
        return ApiResponse.ok(new ReviewCorrectionVO(
                ApiQuerySupport.query(tradeDate, asOfDate, judgementMode, ruleVersion),
                List.of("CYCLE", "MAINLINE", "LEADER", "DIVERGENCE_CONSENSUS", "RISK"),
                List.of(),
                "复盘修正台接口骨架已就绪"
        ));
    }

    @PostMapping("/correction")
    public ApiResponse<ManualCorrectionResult> correct(@RequestBody ManualCorrectionRequest request) {
        return ApiResponse.ok(new ManualCorrectionResult(
                request.correctionId(),
                com.wushi.common.enums.CorrectionStatus.EFFECTIVE,
                request.items() == null ? 0 : request.items().size(),
                LocalDateTime.now()
        ));
    }

    @PostMapping("/evidence-label")
    public ApiResponse<EvidenceLabelResultInfo> labelEvidence(@RequestBody EvidenceLabelRequest request) {
        return ApiResponse.ok(new EvidenceLabelResultInfo(
                request.labelId(),
                request.evidenceId(),
                request.judgementId(),
                request.labelResult(),
                LocalDateTime.now()
        ));
    }

    @PostMapping("/sample-confirmation")
    public ApiResponse<HistoricalSampleConfirmationResult> confirmSample(@RequestBody HistoricalSampleConfirmationRequest request) {
        return ApiResponse.ok(new HistoricalSampleConfirmationResult(
                request.sampleId(),
                request.sampleType(),
                request.confirmedLabel(),
                request.sampleQuality(),
                LocalDateTime.now()
        ));
    }
}
