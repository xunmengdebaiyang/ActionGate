package com.actiongate.worker;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import com.actiongate.trace.Approval;
import com.actiongate.workflow.ActionRequestFingerprint;
import com.actiongate.workflow.AfterSalesWorkflow;
import com.actiongate.workflow.RunResult;
import com.actiongate.workflow.TicketInput;
import io.temporal.api.enums.v1.WorkflowIdReusePolicy;
import io.temporal.testing.TestWorkflowEnvironment;
import io.temporal.client.WorkflowOptions;
import io.temporal.client.WorkflowStub;
import io.temporal.client.WorkflowClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AfterSalesActionWorkflowTest {
    private TestWorkflowEnvironment environment;

    @BeforeEach
    void setUp() {
        environment = TestWorkflowEnvironment.newInstance();
        var worker = environment.newWorker(AfterSalesWorkflow.TASK_QUEUE);
        worker.registerWorkflowImplementationTypes(AfterSalesWorkflowImpl.class);
        worker.registerActivitiesImplementations(new LocalAfterSalesActivities());
        environment.start();
    }

    @AfterEach
    void close() {
        environment.close();
    }

    private record Started(UUID publicId, AfterSalesWorkflow workflow) { }

    private Started workflow() {
        UUID publicId = UUID.randomUUID();
        return new Started(publicId, environment.getWorkflowClient().newWorkflowStub(AfterSalesWorkflow.class, WorkflowOptions.newBuilder()
                .setWorkflowId(AfterSalesWorkflow.WORKFLOW_ID_PREFIX + publicId)
                .setTaskQueue(AfterSalesWorkflow.TASK_QUEUE)
                .setWorkflowIdReusePolicy(WorkflowIdReusePolicy.WORKFLOW_ID_REUSE_POLICY_REJECT_DUPLICATE)
                .setWorkflowExecutionTimeout(Duration.ofMinutes(5)).build()));
    }

    private static Approval approval(UUID runId, TicketInput input, Approval.Decision decision) {
        return new Approval(UUID.randomUUID(), runId, "operator-1", decision,
                Instant.now().plus(Duration.ofHours(1)), "create_refund", ActionRequestFingerprint.refund(input));
    }

    @Test
    void executesApprovedRefund() {
        var started = workflow();
        var input = new TicketInput("10001", TicketInput.Scenario.REFUND_REQUEST,
                new BigDecimal("50.00"), null, "refund-1");
        WorkflowClient.start(started.workflow()::execute, input);
        started.workflow().submitApproval(approval(started.publicId(), input, Approval.Decision.APPROVED));
        var result = WorkflowStub.fromTyped(started.workflow()).getResult(RunResult.class);
        assertEquals(RunResult.Outcome.ACTION_EXECUTED, result.outcome());
        assertEquals(RunResult.Reason.REFUND_EXECUTED, result.reason());
        assertTrue(result.reply().contains("was created"));
    }

    @Test
    void routesRefundWithoutApprovalToHuman() {
        var started = workflow();
        var input = new TicketInput("10001", TicketInput.Scenario.REFUND_REQUEST,
                new BigDecimal("50.00"), null, "refund-2");
        WorkflowClient.start(started.workflow()::execute, input);
        started.workflow().submitApproval(approval(started.publicId(), input, Approval.Decision.REJECTED));
        var result = WorkflowStub.fromTyped(started.workflow()).getResult(RunResult.class);
        assertEquals(RunResult.Outcome.MANUAL_REQUIRED, result.outcome());
        assertEquals(RunResult.Reason.POLICY_BLOCKED, result.reason());
    }

    @Test
    void rejectsRefundAboveOrderAmount() {
        var result = workflow().workflow().execute(new TicketInput("10001", TicketInput.Scenario.REFUND_REQUEST,
                new BigDecimal("200.00"), null, "refund-3"));
        assertEquals(RunResult.Outcome.MANUAL_REQUIRED, result.outcome());
        assertEquals(RunResult.Reason.POLICY_BLOCKED, result.reason());
        assertTrue(result.reply().contains("refund-within-order-amount"));
    }

    @Test
    void executesExchangeAndReturnsIdempotentReplay() {
        var first = workflow().workflow().execute(new TicketInput("10001", TicketInput.Scenario.EXCHANGE_REQUEST,
                null, "SKU-NEW", "exchange-1"));
        var second = workflow().workflow().execute(new TicketInput("10001", TicketInput.Scenario.EXCHANGE_REQUEST,
                null, "SKU-NEW", "exchange-1"));
        assertEquals(RunResult.Outcome.ACTION_EXECUTED, first.outcome());
        assertEquals(RunResult.Outcome.ACTION_REPLAYED, second.outcome());
        assertEquals(RunResult.Reason.IDEMPOTENT_REPLAY, second.reason());
    }
}
