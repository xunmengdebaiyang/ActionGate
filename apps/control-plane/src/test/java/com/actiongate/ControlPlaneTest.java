package com.actiongate;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ControlPlaneTest {
    @Autowired
    private TestRestTemplate client;

    @Test
    void exposesStatusThroughRealHttpServer() {
        var response = client.getForEntity("/api/v1/status", JsonNode.class);
        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals("actiongate", response.getBody().path("service").asText());
        assertEquals("0.2.0", response.getBody().path("version").asText());
    }

    @Test
    void exposesActuatorHealthAfterResourceMigration() {
        var response = client.getForEntity("/actuator/health", JsonNode.class);
        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals("UP", response.getBody().path("status").asText());
    }
}
