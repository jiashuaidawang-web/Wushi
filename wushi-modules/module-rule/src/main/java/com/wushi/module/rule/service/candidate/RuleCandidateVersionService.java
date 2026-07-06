package com.wushi.module.rule.service.candidate;

import com.wushi.common.enums.EngineType;
import com.wushi.module.rule.model.candidate.RuleCandidateApproveRequest;
import com.wushi.module.rule.model.candidate.RuleCandidateGenerateRequest;
import com.wushi.module.rule.model.candidate.RuleCandidateRejectRequest;
import com.wushi.module.rule.model.candidate.RuleCandidateVersion;

import java.util.List;

public interface RuleCandidateVersionService {

    RuleCandidateVersion generateCandidate(RuleCandidateGenerateRequest request);

    RuleCandidateVersion approveCandidate(RuleCandidateApproveRequest request);

    RuleCandidateVersion rejectCandidate(RuleCandidateRejectRequest request);

    RuleCandidateVersion getCandidate(EngineType engineType, String ruleVersion);

    List<RuleCandidateVersion> listCandidates(EngineType engineType, String status);
}
