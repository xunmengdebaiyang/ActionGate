package com.actiongate.policy;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

public record PolicyDocument(String policyId, String version, Status status, List<Rule> rules) {
    public PolicyDocument {
        rules = List.copyOf(rules);
    }

    public enum Status {
        DRAFT, ACTIVE, RETIRED
    }

    public record Rule(String id, When when, @JsonProperty("assert") Assertion assertion, OnViolation onViolation) {
    }

    public enum OnViolation {
        BLOCK
    }

    public record When(String tool, Boolean sideEffect, List<String> toolNotIn) {
        public When {
            if (toolNotIn != null) {
                toolNotIn = List.copyOf(toolNotIn);
            }
        }
    }

    public record Assertion(
            @JsonProperty("approval.status") String approvalStatus,
            @JsonProperty("args.amount_lte") String amountLimit,
            @JsonProperty("args.idempotency_key_present") Boolean idempotencyKeyPresent,
            Boolean always) {
    }
}
