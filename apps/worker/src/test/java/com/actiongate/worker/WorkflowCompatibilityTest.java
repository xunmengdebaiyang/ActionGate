package com.actiongate.worker;

import java.time.Duration;

import com.actiongate.workflow.AfterSalesWorkflow;
import com.actiongate.trace.Approval;
import com.actiongate.workflow.RunResult;
import com.actiongate.workflow.TicketInput;
import io.temporal.testing.WorkflowReplayer;
import io.temporal.workflow.Workflow;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

class WorkflowCompatibilityTest {
    @ParameterizedTest
    @ValueSource(strings = {"consultation_answered", "refund_requires_human", "exchange_requires_human",
            "insufficient_information", "order_not_found", "missing_order_id"})
    void currentWorkerReplaysReleasedV1Histories(String fixture) throws Exception {
        WorkflowReplayer.replayWorkflowExecutionFromResource("history/" + fixture + ".json",
                AfterSalesWorkflowImpl.class);
    }

    @Test
    void incompatibleSchedulingChangeIsDetected() {
        Exception failure = assertThrows(Exception.class, () ->
                WorkflowReplayer.replayWorkflowExecutionFromResource("history/consultation_answered.json",
                        IncompatibleWorkflow.class));
        assertTrue(failure.getMessage().contains("query failure") || failure.getMessage().contains("NonDeterministicException"),
                "Expected replay to report a deterministic command mismatch: " + failure);
    }

    public static class IncompatibleWorkflow implements AfterSalesWorkflow {
        @Override
        public RunResult execute(TicketInput input) {
            Workflow.sleep(Duration.ofSeconds(1));
            return new AfterSalesWorkflowImpl().execute(input);
        }

        @Override
        public void submitApproval(Approval approval) {
            // Deliberately unused in the incompatible replay fixture.
        }
    }
}
