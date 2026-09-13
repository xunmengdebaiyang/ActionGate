package com.actiongate.contract;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;

import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CompiledToolTest {
    private static ToolSpec refund() throws Exception {
        try (var stream = CompiledToolTest.class.getResourceAsStream("/examples/tools/create_refund.json")) {
            assertNotNull(stream);
            return ToolValidator.parseAndValidate(new String(stream.readAllBytes(), StandardCharsets.UTF_8));
        }
    }

    @Test
    void registrationRejectsInvalidDirectlyConstructedSpecs() throws Exception {
        var original = refund();
        var schema = (ObjectNode) original.inputSchema();
        schema.put("$ref", "https://example.com/untrusted");
        var invalid = new ToolSpec(original.toolName(), original.version(), original.sideEffectLevel(), schema);
        assertThrows(ContractViolationException.class, () -> ToolValidator.compile(invalid));
    }

    @Test
    void compiledValidationRetainsStrictChecksAndDefensiveCopies() throws Exception {
        var original = refund();
        var compiled = ToolValidator.compile(original);
        ((ObjectNode) original.inputSchema()).remove("required");
        String valid = "{\"order_id\":\"10001\",\"amount\":20.50,\"idempotency_key\":\"first\"}";
        ((ObjectNode) compiled.validateArguments(valid)).remove("idempotency_key");
        assertEquals("first", compiled.validateArguments(valid).path("idempotency_key").asText());
        for (String invalid : new String[] {
                "{\"order_id\":\"10001\",\"amount\":20}",
                valid.replace("first", "   "), valid.replace("20.50", "-1"),
                valid.replace("20.50", "\"20.50\""), valid.replace("20.50", "20,\"amount\":21"),
                valid.replace("20.50", "20,\"extra\":true")}) {
            assertThrows(ContractViolationException.class, () -> compiled.validateArguments(invalid));
        }
    }

    @Test
    void sameNameAndVersionCannotReuseAnotherSpecsSchema() throws Exception {
        var original = refund();
        var schema = (ObjectNode) original.inputSchema();
        ((ObjectNode) schema.at("/properties/amount")).put("minimum", 100);
        var stricter = new ToolSpec(original.toolName(), original.version(), original.sideEffectLevel(), schema);
        String arguments = "{\"order_id\":\"10001\",\"amount\":20,\"idempotency_key\":\"key\"}";
        assertDoesNotThrow(() -> ToolValidator.compile(original).validateArguments(arguments));
        var compiled = ToolValidator.compile(stricter);
        assertThrows(ContractViolationException.class, () -> compiled.validateArguments(arguments));
    }

    @Test
    void sharedValidatorKeepsConcurrentResultsIndependent() throws Exception {
        var compiled = ToolValidator.compile(refund());
        var tasks = new ArrayList<Callable<Void>>();
        for (int i = 0; i < 64; i++) {
            String key = "request-" + i;
            tasks.add(() -> {
                String arguments = "{\"order_id\":\"10001\",\"amount\":20,\"idempotency_key\":\"" + key + "\"}";
                assertEquals(key, compiled.validateArguments(arguments).path("idempotency_key").asText());
                assertThrows(ContractViolationException.class,
                        () -> compiled.validateArguments(arguments.replace("20", "-1")));
                return null;
            });
        }
        try (var pool = Executors.newFixedThreadPool(8)) {
            for (var future : pool.invokeAll(tasks)) {
                future.get();
            }
        }
    }
}
