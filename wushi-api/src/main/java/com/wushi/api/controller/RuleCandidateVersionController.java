package com.wushi.api.controller;

import com.wushi.common.api.ApiResponse;
import com.wushi.common.enums.EngineType;
import com.wushi.module.rule.model.candidate.RuleCandidateApproveRequest;
import com.wushi.module.rule.model.candidate.RuleCandidateGenerateRequest;
import com.wushi.module.rule.model.candidate.RuleCandidateRejectRequest;
import com.wushi.module.rule.model.candidate.RuleCandidateVersion;
import com.wushi.module.rule.service.candidate.RuleCandidateVersionService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/rule/candidates")
@RequiredArgsConstructor
public class RuleCandidateVersionController {

    private final RuleCandidateVersionService ruleCandidateVersionService;

    @PostMapping("/generate")
    public ApiResponse<RuleCandidateVersion> generate(@RequestBody RuleCandidateGenerateRequest request) {
        return ApiResponse.ok(ruleCandidateVersionService.generateCandidate(request));
    }

    @GetMapping
    public ApiResponse<List<RuleCandidateVersion>> list(
            @RequestParam(required = false) EngineType engineType,
            @RequestParam(required = false) String status) {
        return ApiResponse.ok(ruleCandidateVersionService.listCandidates(engineType, status));
    }

    @GetMapping("/{ruleVersion}")
    public ApiResponse<RuleCandidateVersion> detail(
            @PathVariable String ruleVersion,
            @RequestParam EngineType engineType) {
        return ApiResponse.ok(ruleCandidateVersionService.getCandidate(engineType, ruleVersion));
    }

    @PostMapping("/{ruleVersion}/approve")
    public ApiResponse<RuleCandidateVersion> approve(
            @PathVariable String ruleVersion,
            @RequestBody RuleCandidateApproveRequest request) {
        return ApiResponse.ok(ruleCandidateVersionService.approveCandidate(new RuleCandidateApproveRequest(
                ruleVersion,
                request.engineType(),
                request.approvedBy(),
                request.effectiveDate(),
                request.approvalRemark()
        )));
    }

    @PostMapping("/{ruleVersion}/reject")
    public ApiResponse<RuleCandidateVersion> reject(
            @PathVariable String ruleVersion,
            @RequestBody RuleCandidateRejectRequest request) {
        return ApiResponse.ok(ruleCandidateVersionService.rejectCandidate(new RuleCandidateRejectRequest(
                ruleVersion,
                request.engineType(),
                request.rejectedBy(),
                request.rejectionReason()
        )));
    }
}
