package com.actiongate.control;

import java.time.Duration;

import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowClientOptions;
import io.temporal.serviceclient.WorkflowServiceStubs;
import io.temporal.serviceclient.WorkflowServiceStubsOptions;
import io.temporal.serviceclient.RpcRetryOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(name = "actiongate.temporal.client-enabled", havingValue = "true", matchIfMissing = true)
class TemporalClientConfiguration {
    @Bean(destroyMethod = "shutdown")
    WorkflowServiceStubs workflowServiceStubs(@Value("${actiongate.temporal.target}") String target,
                                              @Value("${actiongate.temporal.request-timeout:5s}") Duration requestTimeout,
                                              @Value("${actiongate.temporal.tls.enabled:false}") boolean tlsEnabled,
                                              @Value("${actiongate.temporal.tls.trust-cert-path:}") String trustCertPath,
                                              @Value("${actiongate.temporal.tls.client-cert-path:}") String clientCertPath,
                                              @Value("${actiongate.temporal.tls.client-key-path:}") String clientKeyPath,
                                              @Value("${actiongate.temporal.api-key:}") String apiKey,
                                              @Value("${actiongate.temporal.tls.server-name:}") String serverName) {
        Duration rpcTimeout = requestTimeout.compareTo(Duration.ofSeconds(3)) < 0
                ? requestTimeout : Duration.ofSeconds(3);
        var options = TemporalTls.apply(WorkflowServiceStubsOptions.newBuilder().setTarget(target), tlsEnabled,
                trustCertPath, clientCertPath, clientKeyPath, apiKey, serverName)
                .setRpcTimeout(rpcTimeout)
                .setRpcRetryOptions(RpcRetryOptions.newBuilder()
                        .setExpiration(requestTimeout)
                        .setInitialInterval(Duration.ofMillis(100))
                        .setMaximumInterval(Duration.ofSeconds(1))
                        .build())
                .build();
        return WorkflowServiceStubs.newServiceStubs(options);
    }

    @Bean
    WorkflowClient workflowClient(WorkflowServiceStubs stubs,
                                  @Value("${actiongate.temporal.namespace}") String namespace) {
        return WorkflowClient.newInstance(stubs, WorkflowClientOptions.newBuilder().setNamespace(namespace).build());
    }
}
