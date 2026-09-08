package com.actiongate.workflow;

import java.math.BigDecimal;

public record OrderSummary(String orderId, BigDecimal amount, String currency, Status status) {
    public enum Status {
        PROCESSING, SHIPPED
    }
}
