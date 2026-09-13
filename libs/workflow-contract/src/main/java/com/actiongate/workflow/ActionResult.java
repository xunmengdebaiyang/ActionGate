package com.actiongate.workflow;

import java.util.List;

public record ActionResult(Status status, String actionId, String toolName, String message,
                           List<String> policyViolations) {
    public ActionResult {
        policyViolations = policyViolations == null ? List.of() : List.copyOf(policyViolations);
    }

    public enum Status {
        EXECUTED, IDEMPOTENT_REPLAY, CONFLICT, APPROVAL_REQUIRED, REJECTED
    }
}
