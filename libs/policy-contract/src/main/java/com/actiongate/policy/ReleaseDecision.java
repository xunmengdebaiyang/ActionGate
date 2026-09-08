package com.actiongate.policy;

import java.util.List;
import java.util.Objects;

import com.actiongate.contract.VersionReference;

import static com.actiongate.contract.ContractViolationException.require;

public record ReleaseDecision(VersionReference candidateVersion, VersionReference baselineVersion,
                              VersionReference policyVersion, Result result, List<String> violations) {
    public ReleaseDecision {
        Objects.requireNonNull(candidateVersion);
        Objects.requireNonNull(baselineVersion);
        Objects.requireNonNull(policyVersion);
        Objects.requireNonNull(result);
        violations = List.copyOf(violations);
        require(result == Result.PASS ? violations.isEmpty() : !violations.isEmpty(),
                "PASS requires no violations; FAIL requires at least one violation");
    }

    public enum Result {
        PASS, FAIL
    }
}
