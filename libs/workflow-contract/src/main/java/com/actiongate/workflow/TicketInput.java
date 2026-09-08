package com.actiongate.workflow;

public record TicketInput(String orderId, Scenario scenario) {
    public enum Scenario {
        ORDER_STATUS, REFUND_REQUEST, EXCHANGE_REQUEST, INSUFFICIENT_INFORMATION
    }
}
