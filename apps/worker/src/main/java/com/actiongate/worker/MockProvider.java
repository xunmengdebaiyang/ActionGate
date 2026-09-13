package com.actiongate.worker;

import java.math.BigDecimal;
import com.actiongate.workflow.ActionResult;
import com.actiongate.workflow.RunResult.Intent;
import com.actiongate.workflow.TicketInput.Scenario;

public final class MockProvider {
    private final ActionIdempotencyStore actions;

    public MockProvider() {
        this(new InMemoryActionIdempotencyStore());
    }

    public MockProvider(ActionIdempotencyStore actions) {
        this.actions = actions;
    }

    public Intent classify(Scenario scenario) {
        return switch (scenario) {
            case ORDER_STATUS -> Intent.QUESTION;
            case REFUND_REQUEST -> Intent.REFUND;
            case EXCHANGE_REQUEST -> Intent.EXCHANGE;
            case INSUFFICIENT_INFORMATION -> Intent.MANUAL;
        };
    }

    public ActionResult createRefund(String orderId, BigDecimal amount, String idempotencyKey) {
        return execute("create_refund", idempotencyKey, orderId + "|" + amount,
                "Refund " + amount + " CNY for order " + orderId + " was created.");
    }

    public ActionResult createExchange(String orderId, String sku, String idempotencyKey) {
        return execute("create_exchange", idempotencyKey, orderId + "|" + sku,
                "Exchange for order " + orderId + " to SKU " + sku + " was created.");
    }

    private ActionResult execute(String tool, String idempotencyKey, String parameters, String message) {
        return actions.executeOnce(tool, idempotencyKey, sha256(parameters), message);
    }

    private static String sha256(String value) {
        try {
            return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
