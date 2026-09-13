package com.actiongate.worker;

import java.io.File;
import java.io.IOException;

import io.grpc.netty.shaded.io.netty.handler.ssl.SslContextBuilder;
import io.grpc.netty.shaded.io.grpc.netty.NettyChannelBuilder;
import io.temporal.authorization.AuthorizationGrpcMetadataProvider;
import io.temporal.serviceclient.GrpcMetadataProvider;
import io.temporal.serviceclient.WorkflowServiceStubsOptions;

final class TemporalTls {
    private TemporalTls() { }

    static WorkflowServiceStubsOptions.Builder apply(WorkflowServiceStubsOptions.Builder builder,
                                                       boolean enabled, String trustCertPath,
                                                       String clientCertPath, String clientKeyPath,
                                                       String apiKey, String serverName) {
        if (!enabled && apiKey != null && !apiKey.isBlank()) {
            throw new IllegalArgumentException("Temporal API key requires TLS");
        }
        if (enabled) {
            if (clientCertPath != null && !clientCertPath.isBlank()
                    ^ clientKeyPath != null && !clientKeyPath.isBlank()) {
                throw new IllegalArgumentException("Temporal client certificate and private key must be configured together");
            }
            try {
                var ssl = SslContextBuilder.forClient();
                if (trustCertPath != null && !trustCertPath.isBlank()) {
                    ssl.trustManager(new File(trustCertPath));
                }
                if (clientCertPath != null && !clientCertPath.isBlank()
                        && clientKeyPath != null && !clientKeyPath.isBlank()) {
                    ssl.keyManager(new File(clientCertPath), new File(clientKeyPath));
                }
                builder.setEnableHttps(true).setSslContext(ssl.build());
                if (serverName != null && !serverName.isBlank()) {
                    builder.setChannelInitializer(channel -> {
                        if (channel instanceof NettyChannelBuilder netty) {
                            netty.overrideAuthority(serverName);
                        }
                    });
                }
            } catch (IOException exception) {
                throw new IllegalArgumentException("Cannot load Temporal TLS certificates", exception);
            }
        } else {
            builder.setEnableHttps(false);
        }
        if (apiKey != null && !apiKey.isBlank()) {
            GrpcMetadataProvider auth = new AuthorizationGrpcMetadataProvider(() -> "Bearer " + apiKey);
            builder.setGrpcMetadataProviders(java.util.List.of(auth));
        }
        return builder;
    }
}
