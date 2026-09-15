package com.actiongate.workflow;

import java.math.BigDecimal;

public record OrderSummary(String orderId, BigDecimal amount, String currency, Status status) {
    public OrderSummary {
        Money.of(amount, currency);
    }

    public Money money() {
        return Money.of(amount, currency);
    }

    public enum Status {
        PROCESSING, SHIPPED
    }
}
