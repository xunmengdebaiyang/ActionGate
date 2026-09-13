package com.actiongate.workflow;

import java.math.BigDecimal;

public record TicketInput(String orderId, Scenario scenario, BigDecimal amount, String sku,
                          String idempotencyKey, ApprovalStatus approvalStatus) {
    public TicketInput(String orderId, Scenario scenario) {
        this(orderId, scenario, null, null, null, null);
    }

    public TicketInput(String orderId, Scenario scenario, BigDecimal amount, String sku, String idempotencyKey) {
        this(orderId, scenario, amount, sku, idempotencyKey, null);
    }

    public enum Scenario {
        ORDER_STATUS, REFUND_REQUEST, EXCHANGE_REQUEST, INSUFFICIENT_INFORMATION
    }

    public enum ApprovalStatus {
        PENDING, APPROVED, REJECTED
    }
}
