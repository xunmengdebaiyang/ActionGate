package com.actiongate.policy;

import java.util.List;
import java.util.Objects;

public record EvaluationCase(String caseId, String input, String expectedIntent,
                             List<String> allowedTools, boolean approvalRequired,
                             String expectedFinalStatus, List<String> invariants) {
    public EvaluationCase {
        Objects.requireNonNull(caseId);
        Objects.requireNonNull(input);
        allowedTools = List.copyOf(allowedTools);
        invariants = List.copyOf(invariants);
    }
}
