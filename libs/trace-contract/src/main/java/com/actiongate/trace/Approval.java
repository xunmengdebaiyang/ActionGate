package com.actiongate.trace;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import static com.actiongate.contract.ContractViolationException.require;

public record Approval(UUID approvalId, UUID runId, String operator, Decision decision, Instant expiresAt) {
    public Approval {
        Objects.requireNonNull(approvalId);
        Objects.requireNonNull(runId);
        Objects.requireNonNull(decision);
        Objects.requireNonNull(expiresAt);
        require(operator != null && !operator.isBlank(), "Approval operator is required");
    }

    public enum Decision {
        APPROVED, REJECTED
    }
}
