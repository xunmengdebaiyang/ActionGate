package com.actiongate.workflow;

import com.actiongate.contract.VersionReference;
import com.actiongate.trace.RunEvent;
import java.util.List;

public record RunResult(Outcome outcome, Intent intent, Reason reason, String reply, OrderSummary order,
                        VersionReference workflowVersion, VersionReference providerVersion,
                        List<RunEvent> auditEvents) {
    public static final VersionReference WORKFLOW = new VersionReference("after-sales-consultation", "1.0.0");
    public static final VersionReference PROVIDER = new VersionReference("mock-scenarios", "1.0.0");

    public RunResult(Outcome outcome, Intent intent, Reason reason, String reply, OrderSummary order,
                     VersionReference workflowVersion, VersionReference providerVersion) {
        this(outcome, intent, reason, reply, order, workflowVersion, providerVersion, List.of());
    }

    public RunResult {
        auditEvents = auditEvents == null ? List.of() : List.copyOf(auditEvents);
    }

    public RunResult withAuditEvents(List<RunEvent> events) {
        return new RunResult(outcome, intent, reason, reply, order, workflowVersion, providerVersion, events);
    }

    public enum Outcome {
        ANSWERED, ACTION_EXECUTED, ACTION_REPLAYED, MANUAL_REQUIRED
    }

    public enum Intent {
        QUESTION, REFUND, EXCHANGE, MANUAL
    }

    public enum Reason {
        CONSULTATION_ANSWERED, REFUND_REQUIRES_HUMAN, EXCHANGE_REQUIRES_HUMAN,
        REFUND_EXECUTED, EXCHANGE_EXECUTED, IDEMPOTENT_REPLAY, APPROVAL_REQUIRED,
        APPROVAL_EXPIRED, IDEMPOTENCY_CONFLICT, POLICY_BLOCKED, MISSING_ORDER_ID, ORDER_NOT_FOUND,
        INSUFFICIENT_INFORMATION
    }
}
