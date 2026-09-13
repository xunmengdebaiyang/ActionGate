package com.actiongate.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SecurityProperties {
    private final boolean enabled;
    private final String apiKey;
    private final String approvalApiKey;

    public SecurityProperties(@Value("${actiongate.security.enabled:true}") boolean enabled,
                              @Value("${actiongate.security.api-key:}") String apiKey,
                              @Value("${actiongate.security.approval-api-key:}") String approvalApiKey) {
        if (enabled && (apiKey.isBlank() || approvalApiKey.isBlank())) {
            throw new IllegalArgumentException("ActionGate API and approval API keys are required when security is enabled");
        }
        this.enabled = enabled;
        this.apiKey = apiKey;
        this.approvalApiKey = approvalApiKey;
    }

    public boolean enabled() { return enabled; }
    public String apiKey() { return apiKey; }
    public String approvalApiKey() { return approvalApiKey; }
}
