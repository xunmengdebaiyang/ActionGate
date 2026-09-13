package com.actiongate.worker;

import java.time.Duration;

import com.actiongate.workflow.AfterSalesActivities;
import com.actiongate.workflow.AfterSalesActionActivities;
import com.actiongate.workflow.AfterSalesWorkflow;
import com.actiongate.trace.Approval;
import com.actiongate.workflow.ActionRequestFingerprint;
import com.actiongate.workflow.ActionResult;
import com.actiongate.workflow.OrderLookup;
import com.actiongate.workflow.OrderSummary;
import com.actiongate.workflow.RunResult;
import com.actiongate.workflow.RunResult.Intent;
import com.actiongate.workflow.RunResult.Outcome;
import com.actiongate.workflow.RunResult.Reason;
import com.actiongate.workflow.TicketInput;
import io.temporal.activity.ActivityOptions;
import io.temporal.common.RetryOptions;
import io.temporal.failure.ApplicationFailure;
import io.temporal.workflow.Workflow;

public class AfterSalesWorkflowImpl implements AfterSalesWorkflow {
    private Approval approval;
    private final AfterSalesActivities activities = Workflow.newActivityStub(AfterSalesActivities.class,
            ActivityOptions.newBuilder()
                    .setStartToCloseTimeout(Duration.ofSeconds(5))
                    .setScheduleToCloseTimeout(Duration.ofSeconds(20))
                    .setRetryOptions(RetryOptions.newBuilder()
                            .setInitialInterval(Duration.ofSeconds(1))
                            .setMaximumInterval(Duration.ofSeconds(2))
                            .setMaximumAttempts(3)
                    .build())
                    .build());
    private final AfterSalesActionActivities actionActivities = Workflow.newActivityStub(AfterSalesActionActivities.class,
            ActivityOptions.newBuilder()
                    .setStartToCloseTimeout(Duration.ofSeconds(5))
                    .setScheduleToCloseTimeout(Duration.ofSeconds(20))
                    .setRetryOptions(RetryOptions.newBuilder()
                            .setInitialInterval(Duration.ofSeconds(1))
                            .setMaximumInterval(Duration.ofSeconds(2))
                            .setMaximumAttempts(3)
                            .build())
                    .build());

    @Override
    public RunResult execute(TicketInput input) {
        if (input == null || input.scenario() == null) {
            throw ApplicationFailure.newNonRetryableFailure("Scenario is required", "INVALID_TICKET");
        }
        if (input.orderId() == null) {
            return manual(Intent.MANUAL, Reason.MISSING_ORDER_ID, null);
        }
        if (!input.orderId().matches("[0-9]{1,12}")) {
            throw ApplicationFailure.newNonRetryableFailure("Invalid order id", "INVALID_TICKET");
        }
        OrderLookup lookup = activities.queryOrder(input.orderId());
        if (lookup == null || (lookup.found() != (lookup.order() != null))) {
            throw ApplicationFailure.newNonRetryableFailure("Invalid order lookup result", "INVALID_TOOL_RESULT");
        }
        if (!lookup.found()) {
            return manual(Intent.MANUAL, Reason.ORDER_NOT_FOUND, null);
        }
        OrderSummary order = lookup.order();
        if (!input.orderId().equals(order.orderId()) || order.status() == null
                || order.amount() == null || order.amount().signum() < 0 || order.currency() == null) {
            throw ApplicationFailure.newNonRetryableFailure("Inconsistent order result", "INVALID_TOOL_RESULT");
        }
        Intent intent = activities.classify(input.scenario());
        if (intent == null) {
            throw ApplicationFailure.newNonRetryableFailure("Missing provider intent", "INVALID_PROVIDER_OUTPUT");
        }
        return switch (intent) {
            case QUESTION -> new RunResult(Outcome.ANSWERED, intent, Reason.CONSULTATION_ANSWERED,
                    "Order " + order.orderId() + " is " + order.status() + ".", order,
                    RunResult.WORKFLOW, RunResult.PROVIDER);
            case REFUND -> {
                if (input.amount() == null || input.idempotencyKey() == null) {
                    yield manual(intent, Reason.REFUND_REQUIRES_HUMAN, order);
                }
                if (input.amount().compareTo(order.amount()) > 0) {
                    yield manual(intent, Reason.POLICY_BLOCKED, order,
                            "Action blocked by policy. Violations: refund-within-order-amount");
                }
                String fingerprint = ActionRequestFingerprint.refund(input);
                boolean signalled = Workflow.await(Duration.ofMinutes(15), () -> approval != null);
                if (!signalled || approval == null) {
                    yield manual(intent, Reason.APPROVAL_REQUIRED, order);
                }
                if (approval.runId() == null || approval.decision() != Approval.Decision.APPROVED
                        || approval.expiresAt().isBefore(java.time.Instant.ofEpochMilli(Workflow.currentTimeMillis()))
                        || !"create_refund".equals(approval.toolName())
                        || !fingerprint.equals(approval.requestHash())) {
                    yield manual(intent, approval.expiresAt().isBefore(java.time.Instant.ofEpochMilli(Workflow.currentTimeMillis()))
                            ? Reason.APPROVAL_EXPIRED : Reason.POLICY_BLOCKED, order);
                }
                yield actionResult(intent, order, actionActivities.createRefund(order.orderId(), input.amount(),
                        order.amount(), input.idempotencyKey(), approval));
            }
            case EXCHANGE -> {
                if (input.sku() == null || input.idempotencyKey() == null) {
                    yield manual(intent, Reason.EXCHANGE_REQUIRES_HUMAN, order);
                }
                yield actionResult(intent, order, actionActivities.createExchange(order.orderId(), input.sku(),
                        order.amount(), input.idempotencyKey()));
            }
            case MANUAL -> manual(intent, Reason.INSUFFICIENT_INFORMATION, order);
        };
    }

    @Override
    public void submitApproval(Approval candidate) {
        String workflowId = Workflow.getInfo().getWorkflowId();
        if (candidate == null || approval != null
                || !workflowId.equals(AfterSalesWorkflow.WORKFLOW_ID_PREFIX + candidate.runId())) {
            return;
        }
        approval = candidate;
    }

    private RunResult actionResult(Intent intent, OrderSummary order, ActionResult action) {
        if (action == null || action.status() == null) {
            throw ApplicationFailure.newNonRetryableFailure("Invalid action result", "INVALID_ACTION_RESULT");
        }
        return switch (action.status()) {
            case EXECUTED -> new RunResult(Outcome.ACTION_EXECUTED, intent,
                    intent == Intent.REFUND ? Reason.REFUND_EXECUTED : Reason.EXCHANGE_EXECUTED,
                    action.message(), order, RunResult.WORKFLOW, RunResult.PROVIDER);
            case IDEMPOTENT_REPLAY -> new RunResult(Outcome.ACTION_REPLAYED, intent, Reason.IDEMPOTENT_REPLAY,
                    action.message(), order, RunResult.WORKFLOW, RunResult.PROVIDER);
            case CONFLICT -> manual(intent, Reason.IDEMPOTENCY_CONFLICT, order, action.message());
            case APPROVAL_REQUIRED -> manual(intent, Reason.APPROVAL_REQUIRED, order, action.message());
            case REJECTED -> manual(intent, Reason.POLICY_BLOCKED, order,
                    action.message() + " Violations: " + String.join(",", action.policyViolations()));
        };
    }

    private RunResult manual(Intent intent, Reason reason, OrderSummary order) {
        return manual(intent, reason, order, "Human review is required. No refund or exchange was executed.");
    }

    private RunResult manual(Intent intent, Reason reason, OrderSummary order, String reply) {
        return new RunResult(Outcome.MANUAL_REQUIRED, intent, reason,
                reply, order,
                RunResult.WORKFLOW, RunResult.PROVIDER);
    }
}
