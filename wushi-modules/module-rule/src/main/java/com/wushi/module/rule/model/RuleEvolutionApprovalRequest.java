package com.wushi.module.rule.model;

public record RuleEvolutionApprovalRequest(
        String candidateId,
        String operator,
        String approvalComment
) {
    public String resolvedOperator() {
        return operator == null || operator.isBlank() ? "manual" : operator;
    }
}
