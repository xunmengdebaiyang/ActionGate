package com.actiongate.control;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import com.actiongate.api.RunView;
import com.actiongate.workflow.AfterSalesWorkflow;
import com.actiongate.workflow.RunResult;
import com.actiongate.workflow.TicketInput;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.temporal.api.common.v1.WorkflowExecution;
import io.temporal.api.enums.v1.WorkflowExecutionStatus;
import io.temporal.api.enums.v1.WorkflowIdReusePolicy;
import io.temporal.api.workflow.v1.WorkflowExecutionInfo;
import io.temporal.api.workflowservice.v1.DescribeWorkflowExecutionRequest;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowOptions;
import io.temporal.client.WorkflowStub;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class RunsService {
    private final WorkflowClient client;
    private final String queue;

    public RunsService(WorkflowClient client, @Value("${actiongate.temporal.task-queue}") String queue) {
        this.client = client;
        this.queue = queue;
    }

    public RunView start(TicketInput input) {
        UUID runId = UUID.randomUUID();
        var workflow = client.newWorkflowStub(AfterSalesWorkflow.class, WorkflowOptions.newBuilder()
                .setWorkflowId(AfterSalesWorkflow.WORKFLOW_ID_PREFIX + runId)
                .setWorkflowIdReusePolicy(WorkflowIdReusePolicy.WORKFLOW_ID_REUSE_POLICY_REJECT_DUPLICATE)
                .setTaskQueue(queue)
                .setWorkflowExecutionTimeout(Duration.ofMinutes(5))
                .build());
        WorkflowExecution execution = WorkflowClient.start(workflow::execute, input);
        return new RunView(runId, execution.getRunId(), RunView.Status.RUNNING, RunResult.WORKFLOW, null, null);
    }

    public RunView get(UUID runId) {
        String workflowId = AfterSalesWorkflow.WORKFLOW_ID_PREFIX + runId;
        var request = DescribeWorkflowExecutionRequest.newBuilder()
                .setNamespace(client.getOptions().getNamespace())
                .setExecution(WorkflowExecution.newBuilder().setWorkflowId(workflowId))
                .build();
        final WorkflowExecutionInfo info;
        try {
            info = client.getWorkflowServiceStubs().blockingStub().withDeadlineAfter(3, TimeUnit.SECONDS)
                    .describeWorkflowExecution(request).getWorkflowExecutionInfo();
        } catch (StatusRuntimeException exception) {
            if (exception.getStatus().getCode() == Status.Code.NOT_FOUND) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Run not found");
            }
            throw exception;
        }
        if (!info.getType().getName().equals(AfterSalesWorkflow.TYPE)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Run not found");
        }
        RunView.Status status = status(info.getStatus());
        RunResult result = null;
        if (status == RunView.Status.COMPLETED) {
            var workflow = client.newWorkflowStub(AfterSalesWorkflow.class, workflowId,
                    Optional.of(info.getExecution().getRunId()));
            try {
                result = WorkflowStub.fromTyped(workflow).getResult(3, TimeUnit.SECONDS, RunResult.class);
            } catch (TimeoutException exception) {
                throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Result is temporarily unavailable");
            }
        }
        String error = switch (status) {
            case FAILED -> "WORKFLOW_FAILED";
            case TIMED_OUT -> "WORKFLOW_TIMED_OUT";
            case TERMINATED -> "WORKFLOW_TERMINATED";
            default -> null;
        };
        return new RunView(runId, info.getExecution().getRunId(), status, RunResult.WORKFLOW, result, error);
    }

    private RunView.Status status(WorkflowExecutionStatus status) {
        return switch (status) {
            case WORKFLOW_EXECUTION_STATUS_RUNNING -> RunView.Status.RUNNING;
            case WORKFLOW_EXECUTION_STATUS_COMPLETED -> RunView.Status.COMPLETED;
            case WORKFLOW_EXECUTION_STATUS_FAILED -> RunView.Status.FAILED;
            case WORKFLOW_EXECUTION_STATUS_TIMED_OUT -> RunView.Status.TIMED_OUT;
            case WORKFLOW_EXECUTION_STATUS_CANCELED -> RunView.Status.CANCELLED;
            case WORKFLOW_EXECUTION_STATUS_TERMINATED -> RunView.Status.TERMINATED;
            case WORKFLOW_EXECUTION_STATUS_CONTINUED_AS_NEW -> RunView.Status.CONTINUED_AS_NEW;
            default -> throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Unknown workflow status");
        };
    }
}
