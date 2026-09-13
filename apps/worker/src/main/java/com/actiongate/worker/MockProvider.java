package com.actiongate.worker;

import java.math.BigDecimal;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.actiongate.workflow.ActionResult;
import com.actiongate.workflow.RunResult.Intent;
import com.actiongate.workflow.TicketInput.Scenario;

public final class MockProvider {
    private final Map<String, ActionResult> actions = new ConcurrentHashMap<>();

    public Intent classify(Scenario scenario) {
        return switch (scenario) {
            case ORDER_STATUS -> Intent.QUESTION;
            case REFUND_REQUEST -> Intent.REFUND;
            case EXCHANGE_REQUEST -> Intent.EXCHANGE;
            case INSUFFICIENT_INFORMATION -> Intent.MANUAL;
        };
    }

    public ActionResult createRefund(String orderId, BigDecimal amount, String idempotencyKey) {
        return execute("create_refund", idempotencyKey,
                "Refund " + amount + " CNY for order " + orderId + " was created.");
    }

    public ActionResult createExchange(String orderId, String sku, String idempotencyKey) {
        return execute("create_exchange", idempotencyKey,
                "Exchange for order " + orderId + " to SKU " + sku + " was created.");
    }

    private ActionResult execute(String tool, String idempotencyKey, String message) {
        String key = tool + ":" + idempotencyKey;
        ActionResult existing = actions.get(key);
        if (existing != null) {
            return new ActionResult(ActionResult.Status.IDEMPOTENT_REPLAY, existing.actionId(), tool,
                    existing.message(), existing.policyViolations());
        }
        ActionResult created = new ActionResult(ActionResult.Status.EXECUTED,
                tool + "-" + Integer.toUnsignedString(key.hashCode()), tool, message, null);
        ActionResult raced = actions.putIfAbsent(key, created);
        if (raced != null) {
            return new ActionResult(ActionResult.Status.IDEMPOTENT_REPLAY, raced.actionId(), tool,
                    raced.message(), raced.policyViolations());
        }
        return created;
    }
}
