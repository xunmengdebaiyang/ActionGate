package com.actiongate.worker;

import java.util.concurrent.TimeUnit;

import io.grpc.StatusRuntimeException;
import io.temporal.api.workflowservice.v1.DescribeNamespaceRequest;
import io.temporal.client.WorkflowClient;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

@Component("temporal")
public class TemporalHealthIndicator implements HealthIndicator {
    private final WorkflowClient client;

    public TemporalHealthIndicator(WorkflowClient client) {
        this.client = client;
    }

    @Override
    public Health health() {
        try {
            client.getWorkflowServiceStubs().blockingStub().withDeadlineAfter(2, TimeUnit.SECONDS)
                    .describeNamespace(DescribeNamespaceRequest.newBuilder()
                            .setNamespace(client.getOptions().getNamespace()).build());
            return Health.up().build();
        } catch (StatusRuntimeException exception) {
            return Health.down().build();
        }
    }
}
