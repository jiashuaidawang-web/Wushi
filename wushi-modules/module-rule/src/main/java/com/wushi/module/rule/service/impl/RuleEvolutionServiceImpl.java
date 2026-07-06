package com.wushi.module.rule.service.impl;

/**
 * Deprecated compatibility placeholder.
 *
 * <p>The rule evolution production implementation is
 * {@link DefaultRuleEvolutionService}. It keeps the full PRD state machine:
 * PENDING_APPROVAL -> APPROVED -> EFFECTIVE, with REJECTED as a terminal
 * branch. This class intentionally does not implement RuleEvolutionService,
 * so Spring will only register the production service bean.</p>
 */
@Deprecated(forRemoval = true)
public final class RuleEvolutionServiceImpl {
    private RuleEvolutionServiceImpl() {
    }
}
