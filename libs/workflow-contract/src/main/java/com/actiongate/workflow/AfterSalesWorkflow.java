package com.actiongate.workflow;

import com.actiongate.trace.Approval;
import io.temporal.workflow.WorkflowInterface;
import io.temporal.workflow.SignalMethod;
import io.temporal.workflow.WorkflowMethod;

@WorkflowInterface
public interface AfterSalesWorkflow {
    String TYPE = "ActionGateAfterSalesConsultationV1";
    String TASK_QUEUE = "actiongate-after-sales-consultation-v1";
    String WORKFLOW_ID_PREFIX = "actiongate-consultation-";

    @WorkflowMethod(name = TYPE)
    RunResult execute(TicketInput input);

    @SignalMethod(name = "ActionGateApprovalV1")
    void submitApproval(Approval approval);
}
