package com.actiongate.workflow;

import java.util.List;

public record ActionResult(Status status, String actionId, String toolName, String message,
                           List<String> policyViolations, String policyVersion, String policyHash) {
    public ActionResult {
        policyViolations = policyViolations == null ? List.of() : List.copyOf(policyViolations);
    }

    public ActionResult(Status status, String actionId, String toolName, String message,
                        List<String> policyViolations) {
        this(status, actionId, toolName, message, policyViolations, null, null);
    }

    public enum Status {
        EXECUTED, IDEMPOTENT_REPLAY, CONFLICT, APPROVAL_REQUIRED, REJECTED
    }
}
