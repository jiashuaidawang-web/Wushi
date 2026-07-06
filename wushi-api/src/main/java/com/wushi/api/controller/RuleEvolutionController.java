package com.wushi.api.controller;

import com.wushi.common.api.ApiResponse;
import com.wushi.module.rule.model.RuleEvolutionApprovalRequest;
import com.wushi.module.rule.model.RuleEvolutionGenerateRequest;
import com.wushi.module.rule.model.RuleVersionCandidateDetail;
import com.wushi.module.rule.service.RuleEvolutionService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/system/rule-evolution")
@RequiredArgsConstructor
public class RuleEvolutionController {

    private final RuleEvolutionService ruleEvolutionService;

    @PostMapping("/candidates/generate")
    public ApiResponse<List<RuleVersionCandidateDetail>> generate(@RequestBody RuleEvolutionGenerateRequest request) {
        return ApiResponse.ok(ruleEvolutionService.generateCandidates(request));
    }

    @GetMapping("/candidates")
    public ApiResponse<List<RuleVersionCandidateDetail>> candidates(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate statDate,
            @RequestParam(required = false) String ruleVersion) {
        return ApiResponse.ok(ruleEvolutionService.listCandidates(status, statDate, ruleVersion));
    }

    @GetMapping("/candidates/{candidateId}")
    public ApiResponse<RuleVersionCandidateDetail> detail(@PathVariable String candidateId) {
        return ApiResponse.ok(ruleEvolutionService.detail(candidateId));
    }

    @PostMapping("/candidates/{candidateId}/approve")
    public ApiResponse<RuleVersionCandidateDetail> approve(@PathVariable String candidateId,
                                                           @RequestBody(required = false) RuleEvolutionApprovalRequest request) {
        RuleEvolutionApprovalRequest resolved = request == null
                ? new RuleEvolutionApprovalRequest(candidateId, null, null)
                : new RuleEvolutionApprovalRequest(candidateId, request.operator(), request.approvalComment());
        return ApiResponse.ok(ruleEvolutionService.approve(resolved));
    }

    @PostMapping("/candidates/{candidateId}/reject")
    public ApiResponse<RuleVersionCandidateDetail> reject(@PathVariable String candidateId,
                                                          @RequestBody(required = false) RuleEvolutionApprovalRequest request) {
        RuleEvolutionApprovalRequest resolved = request == null
                ? new RuleEvolutionApprovalRequest(candidateId, null, null)
                : new RuleEvolutionApprovalRequest(candidateId, request.operator(), request.approvalComment());
        return ApiResponse.ok(ruleEvolutionService.reject(resolved));
    }

    @PostMapping("/candidates/{candidateId}/activate")
    public ApiResponse<RuleVersionCandidateDetail> activate(@PathVariable String candidateId,
                                                            @RequestBody(required = false) RuleEvolutionApprovalRequest request) {
        RuleEvolutionApprovalRequest resolved = request == null
                ? new RuleEvolutionApprovalRequest(candidateId, null, null)
                : new RuleEvolutionApprovalRequest(candidateId, request.operator(), request.approvalComment());
        return ApiResponse.ok(ruleEvolutionService.activate(resolved));
    }
}
