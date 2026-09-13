package com.actiongate.worker;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.UUID;

import com.actiongate.workflow.AfterSalesWorkflow;
import com.actiongate.workflow.RunResult;
import com.actiongate.workflow.TicketInput;
import io.temporal.api.enums.v1.WorkflowIdReusePolicy;
import io.temporal.testing.TestWorkflowEnvironment;
import io.temporal.client.WorkflowOptions;
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

    private AfterSalesWorkflow workflow() {
        return environment.getWorkflowClient().newWorkflowStub(AfterSalesWorkflow.class, WorkflowOptions.newBuilder()
                .setWorkflowId(UUID.randomUUID().toString())
                .setTaskQueue(AfterSalesWorkflow.TASK_QUEUE)
                .setWorkflowIdReusePolicy(WorkflowIdReusePolicy.WORKFLOW_ID_REUSE_POLICY_REJECT_DUPLICATE)
                .setWorkflowExecutionTimeout(Duration.ofMinutes(5)).build());
    }

    @Test
    void executesApprovedRefund() {
        var result = workflow().execute(new TicketInput("10001", TicketInput.Scenario.REFUND_REQUEST,
                new BigDecimal("50.00"), null, "refund-1", TicketInput.ApprovalStatus.APPROVED));
        assertEquals(RunResult.Outcome.ACTION_EXECUTED, result.outcome());
        assertEquals(RunResult.Reason.REFUND_EXECUTED, result.reason());
        assertTrue(result.reply().contains("was created"));
    }

    @Test
    void routesRefundWithoutApprovalToHuman() {
        var result = workflow().execute(new TicketInput("10001", TicketInput.Scenario.REFUND_REQUEST,
                new BigDecimal("50.00"), null, "refund-2", TicketInput.ApprovalStatus.PENDING));
        assertEquals(RunResult.Outcome.MANUAL_REQUIRED, result.outcome());
        assertEquals(RunResult.Reason.APPROVAL_REQUIRED, result.reason());
    }

    @Test
    void rejectsRefundAboveOrderAmount() {
        var result = workflow().execute(new TicketInput("10001", TicketInput.Scenario.REFUND_REQUEST,
                new BigDecimal("200.00"), null, "refund-3", TicketInput.ApprovalStatus.APPROVED));
        assertEquals(RunResult.Outcome.MANUAL_REQUIRED, result.outcome());
        assertEquals(RunResult.Reason.POLICY_BLOCKED, result.reason());
        assertTrue(result.reply().contains("refund-within-order-amount"));
    }

    @Test
    void executesExchangeAndReturnsIdempotentReplay() {
        var first = workflow().execute(new TicketInput("10001", TicketInput.Scenario.EXCHANGE_REQUEST,
                null, "SKU-NEW", "exchange-1", TicketInput.ApprovalStatus.PENDING));
        var second = workflow().execute(new TicketInput("10001", TicketInput.Scenario.EXCHANGE_REQUEST,
                null, "SKU-NEW", "exchange-1", TicketInput.ApprovalStatus.PENDING));
        assertEquals(RunResult.Outcome.ACTION_EXECUTED, first.outcome());
        assertEquals(RunResult.Outcome.ACTION_REPLAYED, second.outcome());
        assertEquals(RunResult.Reason.IDEMPOTENT_REPLAY, second.reason());
    }
}
