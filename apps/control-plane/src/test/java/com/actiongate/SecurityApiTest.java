package com.actiongate;

import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest(classes = ActionGateApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "actiongate.temporal.client-enabled=false",
                "actiongate.security.enabled=true",
                "actiongate.security.api-key=run-key",
                "actiongate.security.approval-api-key=approval-key",
                "actiongate.rate-limit.enabled=true",
                "actiongate.rate-limit.requests-per-minute=10",
                "actiongate.http.max-body-bytes=256"})
@Import(TemporalTestConfiguration.class)
@TestMethodOrder(OrderAnnotation.class)
class SecurityApiTest {
    @Autowired
    private TestRestTemplate http;

    @Test
    @Order(1)
    void rateLimitReturns429AfterTheConfiguredBudget() {
        for (int i = 0; i < 10; i++) {
            assertEquals(200, http.getForEntity("/api/v1/status", JsonNode.class).getStatusCode().value());
        }
        assertEquals(429, http.getForEntity("/api/v1/status", JsonNode.class).getStatusCode().value());
    }

    @Test
    @Order(2)
    void requiresAuthenticationForRunEndpoints() {
        assertEquals(401, http.getForEntity("/api/v1/runs/" + UUID.randomUUID(), JsonNode.class)
                .getStatusCode().value());
        assertEquals(404, http.exchange("/api/v1/runs/" + UUID.randomUUID(), HttpMethod.GET,
                authorized("run-key", null), JsonNode.class).getStatusCode().value());
    }

    @Test
    @Order(3)
    void approvalEndpointRequiresApprovalAuthority() {
        String path = "/api/v1/runs/" + UUID.randomUUID() + "/approval";
        String json = "{\"operator\":\"alice\",\"decision\":\"APPROVED\","
                + "\"expires_at\":\"2030-01-01T00:00:00Z\",\"tool_name\":\"create_refund\","
                + "\"request_hash\":\"" + "0".repeat(64) + "\"}";
        assertEquals(403, http.exchange(path, HttpMethod.POST, authorized("run-key", json), JsonNode.class)
                .getStatusCode().value());
        assertEquals(404, http.exchange(path, HttpMethod.POST, authorized("approval-key", json), JsonNode.class)
                .getStatusCode().value());
    }

    @Test
    @Order(4)
    void rejectsOversizedRequestBodiesWith413() {
        String json = "{\"order_id\":\"10001\",\"scenario\":\"ORDER_STATUS\",\"padding\":\""
                + "x".repeat(300) + "\"}";
        assertEquals(413, http.exchange("/api/v1/runs", HttpMethod.POST,
                authorized("run-key", json), JsonNode.class).getStatusCode().value());
    }

    private HttpEntity<String> authorized(String key, String json) {
        var headers = new HttpHeaders();
        headers.setBearerAuth(key);
        if (json != null) {
            headers.setContentType(MediaType.APPLICATION_JSON);
        }
        return new HttpEntity<>(json, headers);
    }
}
