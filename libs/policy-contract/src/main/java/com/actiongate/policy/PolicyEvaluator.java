package com.actiongate.policy;

import java.util.ArrayList;
import java.util.List;

/** Evaluates the deliberately small, versioned policy grammar at the action boundary. */
public final class PolicyEvaluator {
    private PolicyEvaluator() {
    }

    public static PolicyDecision evaluate(PolicyDocument policy, PolicyEvaluationContext context) {
        if (policy == null || policy.status() != PolicyDocument.Status.ACTIVE) {
            return new PolicyDecision(false, List.of("policy-not-active"));
        }
        List<String> violations = new ArrayList<>();
        for (PolicyDocument.Rule rule : policy.rules()) {
            if (matches(rule.when(), context) && !assertionHolds(rule.assertion(), context)) {
                violations.add(rule.id());
            }
        }
        return new PolicyDecision(violations.isEmpty(), violations);
    }

    private static boolean matches(PolicyDocument.When when, PolicyEvaluationContext context) {
        if (when.tool() != null) {
            return when.tool().equals(context.tool());
        }
        if (Boolean.TRUE.equals(when.sideEffect())) {
            return context.sideEffect();
        }
        return when.toolNotIn() != null && !when.toolNotIn().contains(context.tool());
    }

    private static boolean assertionHolds(PolicyDocument.Assertion assertion, PolicyEvaluationContext context) {
        if (assertion.approvalStatus() != null) {
            return assertion.approvalStatus().equals(context.approvalStatus());
        }
        if (assertion.amountLimit() != null) {
            return context.amount() != null && context.orderAmount() != null
                    && context.amount().compareTo(context.orderAmount()) <= 0;
        }
        if (assertion.idempotencyKeyPresent() != null) {
            return context.idempotencyKey() != null && !context.idempotencyKey().isBlank();
        }
        // The only supported "always" form is false, which deliberately fails.
        return false;
    }
}
