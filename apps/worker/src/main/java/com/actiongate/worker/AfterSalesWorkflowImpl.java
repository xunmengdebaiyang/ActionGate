package com.actiongate.worker;

import java.time.Duration;

import com.actiongate.workflow.AfterSalesActivities;
import com.actiongate.workflow.AfterSalesWorkflow;
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
            case REFUND -> manual(intent, Reason.REFUND_REQUIRES_HUMAN, order);
            case EXCHANGE -> manual(intent, Reason.EXCHANGE_REQUIRES_HUMAN, order);
            case MANUAL -> manual(intent, Reason.INSUFFICIENT_INFORMATION, order);
        };
    }

    private RunResult manual(Intent intent, Reason reason, OrderSummary order) {
        return new RunResult(Outcome.MANUAL_REQUIRED, intent, reason,
                "Human review is required. No refund or exchange was executed.", order,
                RunResult.WORKFLOW, RunResult.PROVIDER);
    }
}
