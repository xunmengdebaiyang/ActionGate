package com.actiongate.control;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.CancellationException;
import java.util.function.Supplier;

import com.actiongate.api.RunView;
import com.actiongate.workflow.AfterSalesWorkflow;
import com.actiongate.workflow.RunResult;
import com.actiongate.workflow.TicketInput;
import io.grpc.Status;
import io.grpc.Context;
import io.grpc.StatusRuntimeException;
import io.temporal.api.common.v1.WorkflowExecution;
import io.temporal.api.enums.v1.WorkflowExecutionStatus;
import io.temporal.api.enums.v1.WorkflowIdReusePolicy;
import io.temporal.api.workflow.v1.WorkflowExecutionInfo;
import io.temporal.api.workflowservice.v1.DescribeWorkflowExecutionRequest;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowOptions;
import io.temporal.client.WorkflowExecutionAlreadyStarted;
import io.temporal.client.WorkflowStub;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class RunsService {
    private final WorkflowClient client;
    private final String queue;
    private final Duration requestTimeout;
    private final ScheduledExecutorService deadlines;
    private final ExecutorService operations;

    public RunsService(WorkflowClient client, @Value("${actiongate.temporal.task-queue}") String queue,
                       @Value("${actiongate.temporal.request-timeout:5s}") Duration requestTimeout,
                       @Qualifier("temporalRequestDeadlines") ScheduledExecutorService deadlines,
                       @Qualifier("temporalRequestOperations") ExecutorService operations) {
        this.client = client;
        this.queue = queue;
        if (requestTimeout.isNegative() || requestTimeout.isZero() || requestTimeout.compareTo(Duration.ofMinutes(1)) > 0) {
            throw new IllegalArgumentException("Temporal request timeout must be positive and at most one minute");
        }
        this.requestTimeout = requestTimeout;
        this.deadlines = deadlines;
        this.operations = operations;
    }

    public RunView start(TicketInput input, String idempotencyKey) {
        var submission = SubmissionIdentity.from(input, idempotencyKey);
        return withinDeadline(() -> start(input, submission));
    }

    private RunView start(TicketInput input, SubmissionIdentity submission) {
        UUID runId = submission.runId();
        var options = WorkflowOptions.newBuilder()
                .setWorkflowId(AfterSalesWorkflow.WORKFLOW_ID_PREFIX + runId)
                .setWorkflowIdReusePolicy(WorkflowIdReusePolicy.WORKFLOW_ID_REUSE_POLICY_REJECT_DUPLICATE)
                .setTaskQueue(queue)
                .setWorkflowExecutionTimeout(Duration.ofMinutes(5));
        if (submission.fingerprint() != null) {
            options.setMemo(Map.of(SubmissionIdentity.MEMO_KEY, submission.fingerprint()));
        }
        var workflow = client.newWorkflowStub(AfterSalesWorkflow.class, options.build());
        try {
            WorkflowExecution execution = WorkflowClient.start(workflow::execute, input);
            return new RunView(runId, execution.getRunId(), RunView.Status.RUNNING, RunResult.WORKFLOW, null, null);
        } catch (WorkflowExecutionAlreadyStarted exception) {
            var info = describe(runId);
            var fingerprint = info.getMemo().getFieldsMap().get(SubmissionIdentity.MEMO_KEY);
            if (submission.fingerprint() == null || fingerprint == null
                    || !submission.fingerprint().equals(client.getOptions().getDataConverter()
                            .fromPayload(fingerprint, String.class, String.class))) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "Idempotency-Key is already associated with a different request");
            }
            return view(runId, info);
        }
    }

    public RunView get(UUID runId) {
        return withinDeadline(() -> view(runId, describe(runId)));
    }

    private RunView withinDeadline(Supplier<RunView> operation) {
        // Run blocking SDK calls away from the servlet thread and enforce a hard HTTP budget.
        var context = Context.current().withDeadlineAfter(requestTimeout.toNanos(), TimeUnit.NANOSECONDS, deadlines);
        Future<RunView> future = operations.submit(() -> {
            Context previous = context.attach();
            try {
                return operation.get();
            } finally {
                context.detach(previous);
            }
        });
        try {
            return future.get(requestTimeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException exception) {
            future.cancel(true);
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Temporal operation exceeded its request deadline");
        } catch (InterruptedException exception) {
            future.cancel(true);
            Thread.currentThread().interrupt();
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Temporal operation was interrupted");
        } catch (CancellationException exception) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Temporal operation was cancelled");
        } catch (ExecutionException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof RuntimeException runtime) {
                throw runtime;
            }
            throw new IllegalStateException("Temporal operation failed", cause);
        }
    }

    private WorkflowExecutionInfo describe(UUID runId) {
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
        return info;
    }

    private RunView view(UUID runId, WorkflowExecutionInfo info) {
        RunView.Status status = status(info.getStatus());
        RunResult result = null;
        if (status == RunView.Status.COMPLETED) {
            var workflow = client.newWorkflowStub(AfterSalesWorkflow.class, info.getExecution().getWorkflowId(),
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
