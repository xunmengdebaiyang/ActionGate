package com.actiongate.workflow;

import com.actiongate.contract.ContractViolationException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TicketValidatorTest {
    @Test
    void acceptsActionFields() {
        var ticket = TicketValidator.parseAndValidate("""
                {"order_id":"10001","scenario":"REFUND_REQUEST","amount":50.00,
                 "idempotency_key":"refund-1","approval_status":"APPROVED"}
                """);
        assertEquals("10001", ticket.orderId());
        assertEquals(0, ticket.amount().compareTo(new java.math.BigDecimal("50.00")));
        assertEquals(TicketInput.ApprovalStatus.APPROVED, ticket.approvalStatus());
    }

    @Test
    void rejectsInvalidActionArguments() {
        assertThrows(ContractViolationException.class, () -> TicketValidator.parseAndValidate(
                "{\"order_id\":\"10001\",\"scenario\":\"REFUND_REQUEST\",\"amount\":0,\"idempotency_key\":\"r\"}"));
        assertThrows(ContractViolationException.class, () -> TicketValidator.parseAndValidate(
                "{\"order_id\":\"10001\",\"scenario\":\"EXCHANGE_REQUEST\",\"sku\":\"sku-1\",\"idempotency_key\":\"\"}"));
    }
}
