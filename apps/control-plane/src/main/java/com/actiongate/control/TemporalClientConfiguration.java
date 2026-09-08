package com.actiongate.control;

import java.time.Duration;

import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowClientOptions;
import io.temporal.serviceclient.WorkflowServiceStubs;
import io.temporal.serviceclient.WorkflowServiceStubsOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(name = "actiongate.temporal.client-enabled", havingValue = "true", matchIfMissing = true)
class TemporalClientConfiguration {
    @Bean(destroyMethod = "shutdown")
    WorkflowServiceStubs workflowServiceStubs(@Value("${actiongate.temporal.target}") String target) {
        return WorkflowServiceStubs.newServiceStubs(WorkflowServiceStubsOptions.newBuilder()
                .setTarget(target).setRpcTimeout(Duration.ofSeconds(3)).build());
    }

    @Bean
    WorkflowClient workflowClient(WorkflowServiceStubs stubs,
                                  @Value("${actiongate.temporal.namespace}") String namespace) {
        return WorkflowClient.newInstance(stubs, WorkflowClientOptions.newBuilder().setNamespace(namespace).build());
    }
}
