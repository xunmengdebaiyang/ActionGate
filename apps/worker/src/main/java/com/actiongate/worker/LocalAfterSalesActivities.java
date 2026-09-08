package com.actiongate.worker;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import com.actiongate.contract.ContractJson;
import com.actiongate.contract.ContractViolationException;
import com.actiongate.contract.ToolSpec;
import com.actiongate.contract.ToolValidator;
import com.actiongate.workflow.AfterSalesActivities;
import com.actiongate.workflow.OrderLookup;
import com.actiongate.workflow.OrderSummary;
import com.actiongate.workflow.RunResult.Intent;
import com.actiongate.workflow.TicketInput.Scenario;
import io.temporal.failure.ApplicationFailure;

public final class LocalAfterSalesActivities implements AfterSalesActivities {
    private static final Map<String, OrderSummary> ORDERS = Map.of(
            "10001", new OrderSummary("10001", new BigDecimal("199.00"), "CNY", OrderSummary.Status.SHIPPED),
            "10002", new OrderSummary("10002", new BigDecimal("89.90"), "CNY", OrderSummary.Status.PROCESSING));
    private final ToolSpec queryOrder;
    private final MockProvider provider = new MockProvider();

    public LocalAfterSalesActivities() {
        try (var input = getClass().getResourceAsStream("/tools/query_order.json")) {
            if (input == null) {
                throw new IllegalStateException("Bundled query_order contract is missing");
            }
            queryOrder = ToolValidator.parseAndValidate(new String(input.readAllBytes(), StandardCharsets.UTF_8));
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot load query_order contract", exception);
        }
    }

    @Override
    public OrderLookup queryOrder(String orderId) {
        try {
            if (orderId == null) {
                throw new ContractViolationException(java.util.List.of("order_id is required"));
            }
            ToolValidator.validateArguments(queryOrder, ContractJson.tree(Map.of("order_id", orderId)).toString());
        } catch (ContractViolationException exception) {
            throw ApplicationFailure.newNonRetryableFailure("Invalid order query arguments", "INVALID_TOOL_ARGUMENTS");
        }
        OrderSummary order = ORDERS.get(orderId);
        return new OrderLookup(order != null, order);
    }

    @Override
    public Intent classify(Scenario scenario) {
        if (scenario == null) {
            throw ApplicationFailure.newNonRetryableFailure("Scenario is required", "INVALID_PROVIDER_INPUT");
        }
        return provider.classify(scenario);
    }
}
