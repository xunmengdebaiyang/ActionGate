package com.actiongate.worker;

import java.time.Duration;

import com.actiongate.workflow.AfterSalesWorkflow;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowClientOptions;
import io.temporal.serviceclient.WorkflowServiceStubs;
import io.temporal.serviceclient.WorkflowServiceStubsOptions;
import io.temporal.worker.WorkerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class TemporalWorkerConfiguration {
    @Bean(destroyMethod = "shutdown")
    WorkflowServiceStubs workflowServiceStubs(@Value("${actiongate.temporal.target}") String target,
                                              @Value("${actiongate.temporal.tls.enabled:false}") boolean tlsEnabled,
                                              @Value("${actiongate.temporal.tls.trust-cert-path:}") String trustCertPath,
                                              @Value("${actiongate.temporal.tls.client-cert-path:}") String clientCertPath,
                                              @Value("${actiongate.temporal.tls.client-key-path:}") String clientKeyPath,
                                              @Value("${actiongate.temporal.api-key:}") String apiKey,
                                              @Value("${actiongate.temporal.tls.server-name:}") String serverName) {
        var options = TemporalTls.apply(WorkflowServiceStubsOptions.newBuilder().setTarget(target), tlsEnabled,
                trustCertPath, clientCertPath, clientKeyPath, apiKey, serverName)
                .setRpcTimeout(Duration.ofSeconds(3)).build();
        var stubs = WorkflowServiceStubs.newServiceStubs(options);
        try {
            stubs.connect(Duration.ofSeconds(5));
            return stubs;
        } catch (RuntimeException exception) {
            stubs.shutdown();
            throw exception;
        }
    }

    @Bean
    WorkflowClient workflowClient(WorkflowServiceStubs stubs,
                                  @Value("${actiongate.temporal.namespace}") String namespace) {
        return WorkflowClient.newInstance(stubs, WorkflowClientOptions.newBuilder().setNamespace(namespace).build());
    }

    @Bean(initMethod = "start", destroyMethod = "shutdown")
    WorkerFactory workerFactory(WorkflowClient client,
                                @Value("${actiongate.temporal.task-queue}") String queue,
                                JdbcTemplate jdbc,
                                @Value("${actiongate.persistence.tenant-id:default}") String tenantId) {
        var factory = WorkerFactory.newInstance(client);
        var worker = factory.newWorker(queue);
        worker.registerWorkflowImplementationTypes(AfterSalesWorkflowImpl.class);
        worker.registerActivitiesImplementations(new LocalAfterSalesActivities(
                new MockProvider(new JdbcActionIdempotencyStore(jdbc, tenantId))));
        return factory;
    }
}
