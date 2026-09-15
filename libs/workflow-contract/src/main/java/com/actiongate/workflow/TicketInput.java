package com.actiongate.workflow;

import java.math.BigDecimal;

public record TicketInput(String orderId, Scenario scenario, BigDecimal amount, String currency, String sku,
                          String idempotencyKey) {
    public TicketInput {
        currency = currency == null ? "CNY" : currency;
    }

    public TicketInput(String orderId, Scenario scenario) {
        this(orderId, scenario, null, "CNY", null, null);
    }

    public TicketInput(String orderId, Scenario scenario, BigDecimal amount, String sku, String idempotencyKey) {
        this(orderId, scenario, amount, "CNY", sku, idempotencyKey);
    }

    public enum Scenario {
        ORDER_STATUS, REFUND_REQUEST, EXCHANGE_REQUEST, INSUFFICIENT_INFORMATION
    }

}
