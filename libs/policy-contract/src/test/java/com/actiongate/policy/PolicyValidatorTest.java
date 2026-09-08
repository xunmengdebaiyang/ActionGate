package com.actiongate.policy;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Stream;

import com.actiongate.contract.ContractJson;
import com.actiongate.contract.ContractViolationException;
import com.actiongate.contract.VersionReference;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

class PolicyValidatorTest {
    private static ObjectNode example() throws IOException {
        try (var input = PolicyValidatorTest.class.getResourceAsStream("/examples/policies/after-sales-safety-v1.json")) {
            assertNotNull(input);
            return (ObjectNode) ContractJson.parse(new String(input.readAllBytes(), StandardCharsets.UTF_8));
        }
    }

    @Test
    void acceptsPlanCompatiblePolicyAndAllFourRuleForms() throws IOException {
        var policy = PolicyValidator.parseAndValidate(example().toString());
        assertEquals("after-sales-safety", policy.policyId());
        assertEquals(PolicyDocument.Status.ACTIVE, policy.status());
        assertEquals(4, policy.rules().size());
        assertEquals("APPROVED", policy.rules().get(0).assertion().approvalStatus());
        assertEquals("context.order_amount", policy.rules().get(1).assertion().amountLimit());
        assertEquals(true, policy.rules().get(2).when().sideEffect());
        assertEquals(true, policy.rules().get(2).assertion().idempotencyKeyPresent());
        assertEquals(false, policy.rules().get(3).assertion().always());
        assertEquals(3, policy.rules().get(3).when().toolNotIn().size());
        assertEquals(policy, PolicyValidator.parseAndValidate(ContractJson.tree(policy).toString()));
        assertThrows(UnsupportedOperationException.class, () -> policy.rules().clear());
        assertThrows(UnsupportedOperationException.class, () -> policy.rules().get(3).when().toolNotIn().clear());
    }

    static Stream<Consumer<ObjectNode>> invalidPolicies() {
        return Stream.of(
                root -> root.remove("policy_id"),
                root -> root.put("policy_id", " "),
                root -> root.put("version", 1),
                root -> root.put("version", "1"),
                root -> root.put("version", "01.0.0"),
                root -> root.put("status", "APPROVED"),
                root -> root.put("unknown", true),
                root -> root.withArray("rules").removeAll(),
                root -> root.put("rules", "not-an-array"),
                root -> root.withArray("rules").add(root.withArray("rules").get(0).deepCopy()),
                root -> ((ObjectNode) root.withArray("rules").get(0)).remove("when"),
                root -> ((ObjectNode) root.withArray("rules").get(0)).remove("assert"),
                root -> ((ObjectNode) root.withArray("rules").get(0)).put("on_violation", "ALLOW"),
                root -> ((ObjectNode) root.withArray("rules").get(0).path("when")).put("side_effect", true),
                root -> ((ObjectNode) root.withArray("rules").get(0).path("assert")).put("approval.status", "REJECTED"),
                root -> ((ObjectNode) root.withArray("rules").get(1).path("assert")).put("args.amount_lte", "user.limit"),
                root -> ((ObjectNode) root.withArray("rules").get(2).path("assert")).put("args.idempotency_key_present", false),
                root -> ((ObjectNode) root.withArray("rules").get(2).path("when")).put("side_effect", "true"),
                root -> ((ObjectNode) root.withArray("rules").get(3).path("assert")).put("always", true),
                root -> ((ObjectNode) root.withArray("rules").get(3).path("when")).withArray("tool_not_in").removeAll()
        );
    }

    @ParameterizedTest
    @MethodSource("invalidPolicies")
    void rejectsInvalidPolicies(Consumer<ObjectNode> mutate) throws IOException {
        ObjectNode node = example();
        mutate.accept(node);
        assertThrows(ContractViolationException.class, () -> PolicyValidator.parseAndValidate(node.toString()));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "null", "[]", "true", "{}", "{} {}", "{\"policy_id\":\"a\",\"policy_id\":\"b\"}"})
    void rejectsMalformedPolicyDocuments(String json) {
        assertThrows(ContractViolationException.class, () -> PolicyValidator.parseAndValidate(json));
    }

    @Test
    void releaseDecisionCannotPassWithViolationsAndCopiesItsInput() {
        var version = new VersionReference("after-sales", "1.0.0");
        var violations = new ArrayList<>(List.of("refund-requires-approval"));
        assertThrows(ContractViolationException.class, () ->
                new ReleaseDecision(version, version, version, ReleaseDecision.Result.PASS, violations));
        assertThrows(ContractViolationException.class, () ->
                new ReleaseDecision(version, version, version, ReleaseDecision.Result.FAIL, List.of()));
        var decision = new ReleaseDecision(version, version, version, ReleaseDecision.Result.FAIL, violations);
        violations.clear();
        assertEquals(1, decision.violations().size());
    }
}
