package com.actiongate.policy;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PolicyEvaluatorTest {
    private static PolicyDocument policy() throws Exception {
        try (var input = PolicyEvaluatorTest.class.getResourceAsStream("/examples/policies/after-sales-safety-v1.json")) {
            assertNotNull(input);
            return PolicyValidator.parseAndValidate(new String(input.readAllBytes(), StandardCharsets.UTF_8));
        }
    }

    @Test
    void allowsApprovedRefundWithinOrderAmountWithIdempotencyKey() throws Exception {
        var decision = PolicyEvaluator.evaluate(policy(),
                new PolicyEvaluationContext("create_refund", true, new BigDecimal("50.00"),
                        new BigDecimal("100.00"), "refund-1", "APPROVED"));
        assertTrue(decision.allowed());
        assertTrue(decision.violations().isEmpty());
    }

    @Test
    void blocksRefundWithoutApprovalOrWhenAmountExceedsOrder() throws Exception {
        var missingApproval = PolicyEvaluator.evaluate(policy(),
                new PolicyEvaluationContext("create_refund", true, new BigDecimal("50.00"),
                        new BigDecimal("100.00"), "refund-1", "PENDING"));
        assertFalse(missingApproval.allowed());
        assertEquals(java.util.List.of("refund-requires-approval"), missingApproval.violations());

        var tooLarge = PolicyEvaluator.evaluate(policy(),
                new PolicyEvaluationContext("create_refund", true, new BigDecimal("101.00"),
                        new BigDecimal("100.00"), "refund-1", "APPROVED"));
        assertFalse(tooLarge.allowed());
        assertTrue(tooLarge.violations().contains("refund-within-order-amount"));
    }

    @Test
    void blocksSideEffectWithoutIdempotencyAndUnknownTool() throws Exception {
        var missingKey = PolicyEvaluator.evaluate(policy(),
                new PolicyEvaluationContext("create_exchange", true, null,
                        new BigDecimal("100.00"), " ", "PENDING"));
        assertFalse(missingKey.allowed());
        assertTrue(missingKey.violations().contains("side-effect-needs-idempotency"));

        var unknown = PolicyEvaluator.evaluate(policy(),
                new PolicyEvaluationContext("delete_order", true, null,
                        new BigDecimal("100.00"), "delete-1", "APPROVED"));
        assertFalse(unknown.allowed());
        assertTrue(unknown.violations().contains("unknown-tool-denied"));
    }
}
