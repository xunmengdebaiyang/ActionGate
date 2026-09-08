package com.actiongate.trace;

import java.time.Instant;
import java.util.UUID;

import com.actiongate.contract.ContractJson;
import com.actiongate.contract.ContractViolationException;
import com.actiongate.contract.VersionReference;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RunEventTest {
    private static final VersionReference WORKFLOW = new VersionReference("after-sales", "1.0.0");
    private static final VersionReference POLICY = new VersionReference("after-sales-safety", "1.0.0");

    @Test
    void preservesTraceAndVersionCorrelationThroughJson() {
        var event = new RunEvent(UUID.randomUUID(), UUID.randomUUID(), "refund", RunEvent.EventType.POLICY_CHECKED,
                Instant.parse("2026-09-08T00:00:00Z"), "0123456789abcdef0123456789abcdef", "0123456789abcdef",
                WORKFLOW, POLICY);
        var json = ContractJson.tree(event);
        assertTrue(json.has("trace_id"));
        assertTrue(json.has("policy_version"));
        assertEquals("policy_checked", json.path("event_type").asText());
        assertEquals(event, ContractJson.convert(json, RunEvent.class));
    }

    @Test
    void rejectsInvalidTraceIdentifiers() {
        assertThrows(ContractViolationException.class, () -> new RunEvent(UUID.randomUUID(), UUID.randomUUID(),
                "refund", RunEvent.EventType.TOOL_EXECUTED, Instant.now(), "0".repeat(32), "0123456789abcdef",
                WORKFLOW, POLICY));
        assertThrows(ContractViolationException.class, () -> new RunEvent(UUID.randomUUID(), UUID.randomUUID(),
                "refund", RunEvent.EventType.TOOL_EXECUTED, Instant.now(), "0123456789abcdef0123456789abcdef",
                "bad-span", WORKFLOW, POLICY));
    }

    @Test
    void approvalsRequireAnOperatorAndVersionReferencesRequireConcreteVersions() {
        assertThrows(ContractViolationException.class, () -> new Approval(UUID.randomUUID(), UUID.randomUUID(),
                " ", Approval.Decision.APPROVED, Instant.now()));
        assertThrows(ContractViolationException.class, () -> new VersionReference("after-sales", "latest"));
    }
}
