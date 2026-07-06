package com.wushi.module.rule.service;

import com.wushi.module.rule.model.RuleEvolutionApprovalRequest;
import com.wushi.module.rule.model.RuleEvolutionGenerateRequest;
import com.wushi.module.rule.model.RuleVersionCandidateDetail;

import java.time.LocalDate;
import java.util.List;

public interface RuleEvolutionService {

    List<RuleVersionCandidateDetail> generateCandidates(RuleEvolutionGenerateRequest request);

    List<RuleVersionCandidateDetail> listCandidates(String status, LocalDate statDate, String ruleVersion);

    RuleVersionCandidateDetail detail(String candidateId);

    RuleVersionCandidateDetail approve(RuleEvolutionApprovalRequest request);

    RuleVersionCandidateDetail reject(RuleEvolutionApprovalRequest request);

    RuleVersionCandidateDetail activate(RuleEvolutionApprovalRequest request);
}
