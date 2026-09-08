package com.actiongate.trace;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import com.actiongate.contract.VersionReference;
import com.fasterxml.jackson.annotation.JsonProperty;

import static com.actiongate.contract.ContractViolationException.require;

public record RunEvent(UUID eventId, UUID runId, String stepId, EventType eventType, Instant occurredAt,
                       String traceId, String spanId, VersionReference workflowVersion,
                       VersionReference policyVersion) {
    public RunEvent {
        Objects.requireNonNull(eventId);
        Objects.requireNonNull(runId);
        Objects.requireNonNull(eventType);
        Objects.requireNonNull(occurredAt);
        Objects.requireNonNull(workflowVersion);
        Objects.requireNonNull(policyVersion);
        require(stepId != null && !stepId.isBlank(), "Event stepId is required");
        require(traceId != null && traceId.matches("[0-9a-f]{32}") && !traceId.equals("0".repeat(32)),
                "traceId must be a nonzero 32-character lowercase hex value");
        require(spanId != null && spanId.matches("[0-9a-f]{16}") && !spanId.equals("0".repeat(16)),
                "spanId must be a nonzero 16-character lowercase hex value");
    }

    public enum EventType {
        @JsonProperty("run_started") RUN_STARTED,
        @JsonProperty("step_started") STEP_STARTED,
        @JsonProperty("llm_called") LLM_CALLED,
        @JsonProperty("tool_proposed") TOOL_PROPOSED,
        @JsonProperty("policy_checked") POLICY_CHECKED,
        @JsonProperty("approval_requested") APPROVAL_REQUESTED,
        @JsonProperty("approval_decided") APPROVAL_DECIDED,
        @JsonProperty("tool_executed") TOOL_EXECUTED,
        @JsonProperty("step_retried") STEP_RETRIED,
        @JsonProperty("step_failed") STEP_FAILED,
        @JsonProperty("run_cancelled") RUN_CANCELLED,
        @JsonProperty("run_completed") RUN_COMPLETED
    }
}
