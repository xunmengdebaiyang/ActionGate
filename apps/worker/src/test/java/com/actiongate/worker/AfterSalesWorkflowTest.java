package com.actiongate.worker;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import com.actiongate.workflow.AfterSalesActivities;
import com.actiongate.workflow.AfterSalesWorkflow;
import com.actiongate.workflow.OrderLookup;
import com.actiongate.workflow.RunResult;
import com.actiongate.workflow.TicketInput;
import io.temporal.api.enums.v1.WorkflowIdReusePolicy;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowExecutionAlreadyStarted;
import io.temporal.client.WorkflowFailedException;
import io.temporal.client.WorkflowOptions;
import io.temporal.failure.ApplicationFailure;
import io.temporal.testing.TestWorkflowEnvironment;
import io.temporal.testing.WorkflowReplayer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.*;

class AfterSalesWorkflowTest {
    private TestWorkflowEnvironment environment;
    private CountingActivities activities;

    @BeforeEach
    void setUp() {
        environment = TestWorkflowEnvironment.newInstance();
        var worker = environment.newWorker(AfterSalesWorkflow.TASK_QUEUE);
        worker.registerWorkflowImplementationTypes(AfterSalesWorkflowImpl.class);
        activities = new CountingActivities();
        worker.registerActivitiesImplementations(activities);
        environment.start();
    }

    @AfterEach
    void close() {
        environment.close();
    }

    private AfterSalesWorkflow workflow(String id) {
        return environment.getWorkflowClient().newWorkflowStub(AfterSalesWorkflow.class, WorkflowOptions.newBuilder()
                .setWorkflowId(id)
                .setTaskQueue(AfterSalesWorkflow.TASK_QUEUE)
                .setWorkflowIdReusePolicy(WorkflowIdReusePolicy.WORKFLOW_ID_REUSE_POLICY_REJECT_DUPLICATE)
                .setWorkflowExecutionTimeout(Duration.ofMinutes(5)).build());
    }

    @Test
    void answersConsultationAndReplaysRecordedHistory() throws Exception {
        String id = UUID.randomUUID().toString();
        var result = workflow(id).execute(new TicketInput("10001", TicketInput.Scenario.ORDER_STATUS));
        assertEquals(RunResult.Outcome.ANSWERED, result.outcome());
        assertEquals("SHIPPED", result.order().status().name());
        assertEquals(RunResult.WORKFLOW, result.workflowVersion());
        assertEquals(1, activities.queries.get());
        var history = environment.getWorkflowClient().fetchHistory(id);
        WorkflowReplayer.replayWorkflowExecution(history, AfterSalesWorkflowImpl.class);
        assertEquals(1, activities.queries.get(), "Replay must not repeat real Activities");
    }

    @ParameterizedTest
    @CsvSource({
            "REFUND_REQUEST,REFUND_REQUIRES_HUMAN",
            "EXCHANGE_REQUEST,EXCHANGE_REQUIRES_HUMAN",
            "INSUFFICIENT_INFORMATION,INSUFFICIENT_INFORMATION"
    })
    void routesHighRiskOrUnclearRequestsToHuman(TicketInput.Scenario scenario, RunResult.Reason reason) {
        String id = UUID.randomUUID().toString();
        var result = workflow(id).execute(new TicketInput("10001", scenario));
        assertEquals(RunResult.Outcome.MANUAL_REQUIRED, result.outcome());
        assertEquals(reason, result.reason());
        var types = environment.getWorkflowClient().fetchHistory(id).getHistory().getEventsList().stream()
                .filter(event -> event.hasActivityTaskScheduledEventAttributes())
                .map(event -> event.getActivityTaskScheduledEventAttributes().getActivityType().getName())
                .toList();
        assertEquals(List.of("ActionGateQueryOrderV1", "ActionGateMockClassifyV1"), types);
    }

    @Test
    void missingOrderDoesNotInvokeAnyActivity() {
        var result = workflow(UUID.randomUUID().toString())
                .execute(new TicketInput(null, TicketInput.Scenario.ORDER_STATUS));
        assertEquals(RunResult.Reason.MISSING_ORDER_ID, result.reason());
        assertEquals(0, activities.queries.get());
        assertEquals(0, activities.classifications.get());
    }

    @Test
    void unknownOrderReturnsHumanResultWithoutCallingProvider() {
        var result = workflow(UUID.randomUUID().toString())
                .execute(new TicketInput("99999", TicketInput.Scenario.ORDER_STATUS));
        assertEquals(RunResult.Reason.ORDER_NOT_FOUND, result.reason());
        assertEquals(1, activities.queries.get());
        assertEquals(0, activities.classifications.get());
    }

    @Test
    void retriesTransientReadFailuresWithinTheBound() {
        activities.failures = 2;
        var result = workflow(UUID.randomUUID().toString())
                .execute(new TicketInput("10001", TicketInput.Scenario.ORDER_STATUS));
        assertEquals(RunResult.Outcome.ANSWERED, result.outcome());
        assertEquals(3, activities.queries.get());
    }

    @Test
    void exhaustedActivityRetriesFailTheWorkflow() {
        activities.failures = 10;
        assertThrows(WorkflowFailedException.class, () -> workflow(UUID.randomUUID().toString())
                .execute(new TicketInput("10001", TicketInput.Scenario.ORDER_STATUS)));
        assertEquals(3, activities.queries.get());
        assertEquals(0, activities.classifications.get());
    }

    @Test
    void nonRetryableActivityFailureRunsOnlyOnce() {
        activities.failures = 10;
        activities.nonRetryable = true;
        assertThrows(WorkflowFailedException.class, () -> workflow(UUID.randomUUID().toString())
                .execute(new TicketInput("10001", TicketInput.Scenario.ORDER_STATUS)));
        assertEquals(1, activities.queries.get());
    }

    @Test
    void invalidInputAndProviderOutputBecomeTerminalFailures() {
        assertThrows(WorkflowFailedException.class, () -> workflow(UUID.randomUUID().toString())
                .execute(new TicketInput("10001", null)));
        assertEquals(0, activities.queries.get());
        activities.invalidProvider = true;
        assertThrows(WorkflowFailedException.class, () -> workflow(UUID.randomUUID().toString())
                .execute(new TicketInput("10001", TicketInput.Scenario.ORDER_STATUS)));
        assertEquals(1, activities.queries.get());
    }

    @Test
    void inconsistentToolResponseFailsBeforeClassification() {
        activities.invalidLookup = true;
        assertThrows(WorkflowFailedException.class, () -> workflow(UUID.randomUUID().toString())
                .execute(new TicketInput("10001", TicketInput.Scenario.ORDER_STATUS)));
        assertEquals(0, activities.classifications.get());
    }

    @Test
    void duplicateWorkflowIdIsRejectedAfterCompletion() {
        String id = UUID.randomUUID().toString();
        var input = new TicketInput("10001", TicketInput.Scenario.ORDER_STATUS);
        workflow(id).execute(input);
        var duplicate = workflow(id);
        assertThrows(WorkflowExecutionAlreadyStarted.class, () -> WorkflowClient.start(duplicate::execute, input));
    }

    private static final class CountingActivities implements AfterSalesActivities {
        private final LocalAfterSalesActivities delegate = new LocalAfterSalesActivities();
        private final AtomicInteger queries = new AtomicInteger();
        private final AtomicInteger classifications = new AtomicInteger();
        private int failures;
        private boolean nonRetryable;
        private boolean invalidProvider;
        private boolean invalidLookup;

        @Override
        public OrderLookup queryOrder(String orderId) {
            int attempt = queries.incrementAndGet();
            if (attempt <= failures) {
                if (nonRetryable) {
                    throw ApplicationFailure.newNonRetryableFailure("Injected read error", "PERMANENT_READ_ERROR");
                }
                throw ApplicationFailure.newFailure("Injected read error", "TRANSIENT_READ_ERROR");
            }
            return invalidLookup ? new OrderLookup(true, null) : delegate.queryOrder(orderId);
        }

        @Override
        public RunResult.Intent classify(TicketInput.Scenario scenario) {
            classifications.incrementAndGet();
            return invalidProvider ? null : delegate.classify(scenario);
        }
    }
}
