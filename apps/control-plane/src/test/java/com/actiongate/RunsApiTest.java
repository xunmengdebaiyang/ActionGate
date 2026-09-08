package com.actiongate;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import com.actiongate.workflow.AfterSalesWorkflow;
import com.actiongate.workflow.RunResult;
import com.actiongate.workflow.TicketInput;
import com.fasterxml.jackson.databind.JsonNode;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowFailedException;
import io.temporal.client.WorkflowOptions;
import io.temporal.client.WorkflowStub;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(classes = ActionGateApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "actiongate.temporal.client-enabled=false")
@Import(TemporalTestConfiguration.class)
class RunsApiTest {
    @Autowired private TestRestTemplate http;
    @Autowired private WorkflowClient temporal;
    @Autowired private ApplicationContext context;

    private HttpEntity<String> body(String json) {
        var headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return new HttpEntity<>(json, headers);
    }

    @ParameterizedTest
    @ValueSource(strings = {"ORDER_STATUS", "REFUND_REQUEST", "EXCHANGE_REQUEST", "INSUFFICIENT_INFORMATION"})
    void submitAndQueryUsesTemporalResult(String scenario) throws Exception {
        var accepted = http.postForEntity("/api/v1/runs",
                body("{\"order_id\":\"10001\",\"scenario\":\"" + scenario + "\"}"), JsonNode.class);
        assertEquals(202, accepted.getStatusCode().value());
        String runId = accepted.getBody().path("run_id").asText();
        assertFalse(runId.isBlank());
        String path = "/api/v1/runs/" + runId;
        assertEquals(path, accepted.getHeaders().getLocation().toString());
        var workflow = temporal.newWorkflowStub(AfterSalesWorkflow.class,
                AfterSalesWorkflow.WORKFLOW_ID_PREFIX + runId,
                Optional.of(accepted.getBody().path("temporal_run_id").asText()));
        WorkflowStub.fromTyped(workflow).getResult(20, TimeUnit.SECONDS, RunResult.class);
        var response = http.getForEntity(path, JsonNode.class);
        assertEquals(200, response.getStatusCode().value());
        assertEquals("COMPLETED", response.getBody().path("status").asText());
        String outcome = scenario.equals("ORDER_STATUS") ? "ANSWERED" : "MANUAL_REQUIRED";
        assertEquals(outcome, response.getBody().at("/result/outcome").asText());
        assertEquals("1.0.0", response.getBody().at("/workflow_version/version").asText());
        assertFalse(context.containsBean("workerFactory"), "Control plane must not start a production worker");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{}", "{", "null", "[]", "{} {}",
            "{\"scenario\":\"REFUND_REQUEST\",\"order_id\":10001}",
            "{\"scenario\":\"ORDER_STATUS\",\"order_id\":\"\"}",
            "{\"scenario\":\"UNKNOWN\"}",
            "{\"scenario\":\"ORDER_STATUS\",\"message\":\"private customer text\"}",
            "{\"scenario\":\"ORDER_STATUS\",\"scenario\":\"REFUND_REQUEST\"}"
    })
    void rejectsInvalidTicketsBeforeStartingExecution(String json) {
        var response = http.postForEntity("/api/v1/runs", body(json), JsonNode.class);
        assertEquals(400, response.getStatusCode().value());
        assertEquals("INVALID_TICKET", response.getBody().path("code").asText());
    }

    @Test
    void missingRunAndInvalidRunIdHaveDistinctClientErrors() {
        assertEquals(404, http.getForEntity("/api/v1/runs/" + UUID.randomUUID(), JsonNode.class).getStatusCode().value());
        assertEquals(400, http.getForEntity("/api/v1/runs/not-a-uuid", JsonNode.class).getStatusCode().value());
    }

    @Test
    void exposesRunningAndTimeoutForUnservedQueue() {
        UUID id = UUID.randomUUID();
        var workflow = temporal.newWorkflowStub(AfterSalesWorkflow.class, WorkflowOptions.newBuilder()
                .setWorkflowId(AfterSalesWorkflow.WORKFLOW_ID_PREFIX + id)
                .setTaskQueue("unserved-test-queue")
                .setWorkflowExecutionTimeout(Duration.ofSeconds(30)).build());
        WorkflowClient.start(workflow::execute, new TicketInput("10001", TicketInput.Scenario.ORDER_STATUS));
        assertEquals("RUNNING", http.getForObject("/api/v1/runs/" + id, JsonNode.class).path("status").asText());
        assertThrows(WorkflowFailedException.class, () -> WorkflowStub.fromTyped(workflow).getResult(RunResult.class));
        var result = http.getForObject("/api/v1/runs/" + id, JsonNode.class);
        assertEquals("TIMED_OUT", result.path("status").asText());
        assertEquals("WORKFLOW_TIMED_OUT", result.path("error_code").asText());
    }

    @Test
    void exposesFailedExecutionWithoutLeakingFailureDetails() {
        UUID id = UUID.randomUUID();
        var workflow = temporal.newWorkflowStub(AfterSalesWorkflow.class, WorkflowOptions.newBuilder()
                .setWorkflowId(AfterSalesWorkflow.WORKFLOW_ID_PREFIX + id)
                .setTaskQueue(AfterSalesWorkflow.TASK_QUEUE).build());
        assertThrows(WorkflowFailedException.class, () -> workflow.execute(new TicketInput("10001", null)));
        var result = http.getForObject("/api/v1/runs/" + id, JsonNode.class);
        assertEquals("FAILED", result.path("status").asText());
        assertEquals("WORKFLOW_FAILED", result.path("error_code").asText());
        assertFalse(result.has("result"));
    }
}
