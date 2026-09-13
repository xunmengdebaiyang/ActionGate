package com.actiongate.trace;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import static com.actiongate.contract.ContractViolationException.require;

public record Approval(UUID approvalId, UUID runId, String operator, Decision decision, Instant expiresAt,
                       String toolName, String requestHash) {
    public Approval {
        Objects.requireNonNull(approvalId);
        Objects.requireNonNull(runId);
        Objects.requireNonNull(decision);
        Objects.requireNonNull(expiresAt);
        require(operator != null && !operator.isBlank(), "Approval operator is required");
        require(toolName != null && toolName.matches("[a-z][a-z0-9_-]{0,63}"),
                "Approval toolName is invalid");
        require(requestHash != null && requestHash.matches("[0-9a-f]{64}"),
                "Approval requestHash must be a SHA-256 hex digest");
    }

    public Approval(UUID approvalId, UUID runId, String operator, Decision decision, Instant expiresAt) {
        this(approvalId, runId, operator, decision, expiresAt, "create_refund", "0".repeat(64));
    }

    public enum Decision {
        APPROVED, REJECTED
    }
}
