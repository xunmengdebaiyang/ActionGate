package com.actiongate.trace;

import java.util.Objects;
import java.util.UUID;

import com.actiongate.contract.VersionReference;

public record AgentRun(UUID runId, VersionReference workflowVersion, VersionReference policyVersion, Status status) {
    public AgentRun {
        Objects.requireNonNull(runId);
        Objects.requireNonNull(workflowVersion);
        Objects.requireNonNull(policyVersion);
        Objects.requireNonNull(status);
    }

    public enum Status {
        CREATED, RUNNING, WAITING_APPROVAL, COMPLETED, FAILED_RETRYABLE, FAILED_FINAL,
        APPROVAL_REJECTED, APPROVAL_EXPIRED, CANCEL_REQUESTED, CANCELLED, DEAD_LETTER,
        RECONCILIATION_REQUIRED
    }
}
