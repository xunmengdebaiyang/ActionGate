package com.actiongate.policy;

import java.util.List;

public record PolicyDecision(boolean allowed, List<String> violations) {
    public PolicyDecision {
        violations = violations == null ? List.of() : List.copyOf(violations);
    }
}
