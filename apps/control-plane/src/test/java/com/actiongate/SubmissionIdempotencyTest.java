package com.actiongate;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ExecutorService;
import java.time.Duration;

import com.actiongate.control.RunsService;
import com.actiongate.workflow.AfterSalesWorkflow;
import com.actiongate.workflow.RunResult;
import com.actiongate.workflow.TicketInput;
import com.fasterxml.jackson.databind.JsonNode;
import io.temporal.client.WorkflowClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(classes = ActionGateApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"actiongate.temporal.client-enabled=false", "actiongate.security.enabled=false",
                "actiongate.rate-limit.enabled=false"})
@Import(TemporalTestConfiguration.class)
class SubmissionIdempotencyTest {
    private static final String TICKET = "{\"order_id\":\"10001\",\"scenario\":\"ORDER_STATUS\"}";
    @Autowired private TestRestTemplate http;
    @Autowired private WorkflowClient temporal;
    @Autowired @Qualifier("temporalRequestDeadlines") private ScheduledExecutorService deadlines;
    @Autowired @Qualifier("temporalRequestOperations") private ExecutorService operations;

    private HttpEntity<String> body(String json, String key) {
        var headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (key != null) {
            headers.set("Idempotency-Key", key);
        }
        return new HttpEntity<>(json, headers);
    }

    @Test
    void retriesReturnTheOriginalCompletedExecutionAndRejectDifferentInput() throws Exception {
        String key = UUID.randomUUID().toString();
        var original = http.postForEntity("/api/v1/runs", body(TICKET, key), JsonNode.class);
        assertEquals(202, original.getStatusCode().value());
        String id = original.getBody().path("run_id").asText();
        var stub = temporal.newUntypedWorkflowStub(AfterSalesWorkflow.WORKFLOW_ID_PREFIX + id);
        stub.getResult(20, TimeUnit.SECONDS, RunResult.class);
        var retry = http.postForEntity("/api/v1/runs",
                body("{ \"scenario\" : \"ORDER_STATUS\", \"order_id\" : \"10001\" }", key), JsonNode.class);
        assertEquals(202, retry.getStatusCode().value());
        assertEquals(original.getHeaders().getLocation(), retry.getHeaders().getLocation());
        assertEquals(id, retry.getBody().path("run_id").asText());
        assertEquals(original.getBody().path("temporal_run_id"), retry.getBody().path("temporal_run_id"));
        assertEquals("COMPLETED", retry.getBody().path("status").asText());
        assertEquals("ANSWERED", retry.getBody().at("/result/outcome").asText());
        assertEquals(409, http.postForEntity("/api/v1/runs",
                body(TICKET.replace("ORDER_STATUS", "REFUND_REQUEST"), key), JsonNode.class).getStatusCode().value());
        assertEquals(409, http.postForEntity("/api/v1/runs",
                body(TICKET.replace("10001", "10002"), key), JsonNode.class).getStatusCode().value());
    }

    @Test
    void concurrentRequestsCreateOnlyOneExecution() throws Exception {
        String key = UUID.randomUUID().toString();
        var tasks = new ArrayList<Callable<String>>();
        for (int i = 0; i < 8; i++) {
            tasks.add(() -> {
                var response = http.postForEntity("/api/v1/runs", body(TICKET, key), JsonNode.class);
                assertEquals(202, response.getStatusCode().value());
                return response.getBody().path("run_id").asText() + ":" + response.getBody().path("temporal_run_id").asText();
            });
        }
        var executions = new HashSet<String>();
        try (var pool = Executors.newFixedThreadPool(8)) {
            for (var future : pool.invokeAll(tasks)) {
                executions.add(future.get());
            }
        }
        assertEquals(1, executions.size());
    }

    @Test
    void newClientAndServiceRecoverAcceptedRequestWithoutAnInMemoryRegistry() {
        String key = UUID.randomUUID().toString();
        var input = new TicketInput("10001", TicketInput.Scenario.ORDER_STATUS);
        var firstService = new RunsService(temporal, "idempotency-unserved", Duration.ofSeconds(5), deadlines, operations);
        var accepted = firstService.start(input, key);
        // Model a lost HTTP response followed by a request to a fresh control-plane instance.
        var replacement = WorkflowClient.newInstance(temporal.getWorkflowServiceStubs(), temporal.getOptions());
        var retry = new RunsService(replacement, "idempotency-unserved", Duration.ofSeconds(5), deadlines, operations).start(input, key);
        assertEquals(accepted.runId(), retry.runId());
        assertEquals(accepted.temporalRunId(), retry.temporalRunId());
        assertEquals(accepted.status(), retry.status());
    }

    @Test
    void omittedAndNullOrderIdsAreTheSameValidatedRequest() {
        String key = UUID.randomUUID().toString();
        var first = http.postForEntity("/api/v1/runs", body("{\"scenario\":\"ORDER_STATUS\"}", key), JsonNode.class);
        var retry = http.postForEntity("/api/v1/runs",
                body("{\"scenario\":\"ORDER_STATUS\",\"order_id\":null}", key), JsonNode.class);
        assertEquals(202, first.getStatusCode().value());
        assertEquals(202, retry.getStatusCode().value());
        assertEquals(first.getBody().path("temporal_run_id"), retry.getBody().path("temporal_run_id"));
    }

    @Test
    void differentOrAbsentKeysCreateIndependentRuns() {
        var ids = new HashSet<String>();
        for (String key : new String[] {null, null, UUID.randomUUID().toString(), UUID.randomUUID().toString()}) {
            var response = http.postForEntity("/api/v1/runs", body(TICKET, key), JsonNode.class);
            assertEquals(202, response.getStatusCode().value());
            ids.add(response.getBody().path("run_id").asText());
        }
        assertEquals(4, ids.size());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "two keys", "a,b", "bad/key"})
    void rejectsInvalidKeys(String key) {
        assertEquals(400, http.postForEntity("/api/v1/runs", body(TICKET, key), JsonNode.class).getStatusCode().value());
    }

    @Test
    void boundsKeyLength() {
        assertEquals(202, http.postForEntity("/api/v1/runs", body(TICKET, "x".repeat(128)), JsonNode.class).getStatusCode().value());
        assertEquals(400, http.postForEntity("/api/v1/runs", body(TICKET, "x".repeat(129)), JsonNode.class).getStatusCode().value());
    }
}
