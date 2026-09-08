package com.actiongate.contract;

import java.util.List;

public final class ContractViolationException extends IllegalArgumentException {
    private final List<String> violations;

    public ContractViolationException(List<String> violations) {
        super(String.join("; ", violations));
        this.violations = List.copyOf(violations);
    }

    public List<String> violations() {
        return violations;
    }

    public static void require(boolean valid, String message) {
        if (!valid) {
            throw new ContractViolationException(List.of(message));
        }
    }
}
