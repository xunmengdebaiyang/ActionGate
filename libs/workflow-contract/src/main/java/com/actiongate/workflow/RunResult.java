package com.actiongate.workflow;

import com.actiongate.contract.VersionReference;

public record RunResult(Outcome outcome, Intent intent, Reason reason, String reply, OrderSummary order,
                        VersionReference workflowVersion, VersionReference providerVersion) {
    public static final VersionReference WORKFLOW = new VersionReference("after-sales-consultation", "1.0.0");
    public static final VersionReference PROVIDER = new VersionReference("mock-scenarios", "1.0.0");

    public enum Outcome {
        ANSWERED, MANUAL_REQUIRED
    }

    public enum Intent {
        QUESTION, REFUND, EXCHANGE, MANUAL
    }

    public enum Reason {
        CONSULTATION_ANSWERED, REFUND_REQUIRES_HUMAN, EXCHANGE_REQUIRES_HUMAN,
        MISSING_ORDER_ID, ORDER_NOT_FOUND, INSUFFICIENT_INFORMATION
    }
}
