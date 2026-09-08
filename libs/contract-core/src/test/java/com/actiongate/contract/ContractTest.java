package com.actiongate.contract;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Stream;

import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

class ContractTest {
    private static String example(String path) throws IOException {
        try (var input = ContractTest.class.getResourceAsStream("/examples/" + path)) {
            assertNotNull(input, path);
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static ObjectNode workflow() throws IOException {
        return (ObjectNode) ContractJson.parse(example("workflows/after-sales-v1.json"));
    }

    private static ObjectNode refund() throws IOException {
        return (ObjectNode) ContractJson.parse(example("tools/create_refund.json"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"query_order", "create_refund", "create_exchange"})
    void validatesRealToolExamples(String name) throws IOException {
        assertEquals(name, ToolValidator.parseAndValidate(example("tools/" + name + ".json")).toolName());
    }

    @Test
    void validatesWorkflowAgainstRealToolCatalog() throws IOException {
        Map<String, ToolSpec> catalog = new HashMap<>();
        for (String name : new String[] {"query_order", "create_refund", "create_exchange"}) {
            catalog.put(name, ToolValidator.parseAndValidate(example("tools/" + name + ".json")));
        }
        var definition = WorkflowValidator.validateWithTools(workflow().toString(), catalog);
        assertEquals("query-order", definition.entryNode());
        var approval = definition.nodes().stream().filter(n -> n.id().equals("approve-refund")).findFirst().orElseThrow();
        assertEquals("refund", approval.transitions().get("approved"));
        assertThrows(UnsupportedOperationException.class, () -> definition.nodes().clear());
        assertThrows(UnsupportedOperationException.class, () -> approval.transitions().clear());
    }

    @Test
    void validatesConsultationWorkflowExample() throws IOException {
        var definition = WorkflowValidator.parseAndValidate(example("workflows/after-sales-consultation-v1.json"));
        assertEquals("after-sales-consultation", definition.workflowId());
        assertEquals("1.0.0", definition.version());
    }

    @Test
    void rejectsUnknownToolsInCatalog() throws IOException {
        String json = workflow().toString();
        assertThrows(ContractViolationException.class, () -> WorkflowValidator.validateWithTools(json, Map.of()));
    }

    static Stream<Consumer<ObjectNode>> invalidWorkflows() {
        return Stream.of(
                root -> root.remove("workflow_id"),
                root -> root.put("version", "01.0.0"),
                root -> root.put("version", 1),
                root -> root.put("extra", true),
                root -> root.put("entry_node", "missing"),
                root -> ((ObjectNode) root.withArray("nodes").get(0)).put("id", "classify"),
                root -> ((ObjectNode) root.withArray("nodes").get(0).path("transitions")).put("success", "missing"),
                root -> ((ObjectNode) root.withArray("nodes").get(0).path("transitions")).put("success", "query-order"),
                root -> ((ObjectNode) root.withArray("nodes").get(1)).put("type", "SCRIPT"),
                root -> ((ObjectNode) root.withArray("nodes").get(0)).remove("tool_name"),
                root -> ((ObjectNode) root.withArray("nodes").get(1)).put("tool_name", "query_order"),
                root -> ((ObjectNode) root.withArray("nodes").get(0)).set("transitions", ContractJson.parse("{}")),
                root -> ((ObjectNode) root.withArray("nodes").get(5).path("transitions")).put("success", "classify"),
                root -> root.withArray("nodes").add(ContractJson.parse("{\"id\":\"unused\",\"type\":\"END\",\"transitions\":{}}"))
        );
    }

    @ParameterizedTest
    @MethodSource("invalidWorkflows")
    void rejectsInvalidWorkflowContracts(Consumer<ObjectNode> mutate) throws IOException {
        ObjectNode root = workflow();
        mutate.accept(root);
        assertThrows(ContractViolationException.class, () -> WorkflowValidator.parseAndValidate(root.toString()));
    }

    static Stream<Consumer<ObjectNode>> invalidTools() {
        return Stream.of(
                root -> root.remove("tool_name"),
                root -> root.put("side_effect_level", "UNKNOWN"),
                root -> root.put("version", "1"),
                root -> root.put("extra", true),
                root -> ((ObjectNode) root.path("input_schema")).put("$ref", "https://example.com/schema"),
                root -> ((ObjectNode) root.path("input_schema")).put("additionalProperties", true),
                root -> ((ObjectNode) root.path("input_schema")).withArray("required").add("unknown"),
                root -> ((ObjectNode) root.path("input_schema")).withArray("required").add("amount"),
                root -> ((ObjectNode) root.path("input_schema")).set("required", ContractJson.parse("[\"amount\",\"order_id\"]")),
                root -> ((ObjectNode) root.at("/input_schema/properties/idempotency_key")).put("type", "integer"),
                root -> ((ObjectNode) root.at("/input_schema/properties/idempotency_key")).remove("minLength")
        );
    }

    @ParameterizedTest
    @MethodSource("invalidTools")
    void rejectsInvalidToolContracts(Consumer<ObjectNode> mutate) throws IOException {
        ObjectNode root = refund();
        mutate.accept(root);
        assertThrows(ContractViolationException.class, () -> ToolValidator.parseAndValidate(root.toString()));
    }

    @Test
    void validatesActualArgumentsAndProtectsSchemaFromMutation() throws IOException {
        var tool = ToolValidator.parseAndValidate(refund().toString());
        ((ObjectNode) tool.inputSchema()).remove("required");
        var result = ToolValidator.validateArguments(tool,
                "{\"order_id\":\"10001\",\"amount\":20.50,\"idempotency_key\":\"refund-001\"}");
        assertEquals(20.50, result.path("amount").asDouble());
        assertTrue(tool.inputSchema().has("required"));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"order_id\":\"10001\",\"amount\":20}",
            "{\"order_id\":\"10001\",\"amount\":20,\"idempotency_key\":\"\"}",
            "{\"order_id\":\"10001\",\"amount\":20,\"idempotency_key\":\"   \"}",
            "{\"order_id\":\"10001\",\"amount\":-1,\"idempotency_key\":\"a\"}",
            "{\"order_id\":\"10001\",\"amount\":\"20\",\"idempotency_key\":\"a\"}",
            "{\"order_id\":\"10001\",\"amount\":20,\"idempotency_key\":\"a\",\"approved\":true}"
    })
    void rejectsInvalidRefundArguments(String json) throws IOException {
        var tool = ToolValidator.parseAndValidate(refund().toString());
        assertThrows(ContractViolationException.class, () -> ToolValidator.validateArguments(tool, json));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "null", "[]", "123", "{", "{} {}", "{\"version\":\"1\",\"version\":\"2\"}"})
    void rejectsMalformedOrNonObjectDocuments(String json) {
        assertThrows(ContractViolationException.class, () -> WorkflowValidator.parseAndValidate(json));
        assertThrows(ContractViolationException.class, () -> ToolValidator.parseAndValidate(json));
    }

    @Test
    void workflowHashIgnoresWhitespaceButDetectsDefinitionChanges() throws IOException {
        ObjectNode json = workflow();
        var original = WorkflowVersion.fromJson(json.toString());
        assertEquals(original.definitionHash(), WorkflowVersion.fromJson(json.toPrettyString()).definitionHash());
        assertEquals(64, original.definitionHash().length());
        json.put("name", "Changed definition");
        assertNotEquals(original.definitionHash(), WorkflowVersion.fromJson(json.toString()).definitionHash());
    }

    @Test
    void reportsActionableViolations() throws IOException {
        ObjectNode json = workflow();
        json.put("entry_node", "absent");
        var exception = assertThrows(ContractViolationException.class,
                () -> WorkflowValidator.parseAndValidate(json.toString()));
        assertTrue(exception.violations().getFirst().contains("entry_node"));
        assertThrows(UnsupportedOperationException.class, () -> exception.violations().clear());
    }
}
