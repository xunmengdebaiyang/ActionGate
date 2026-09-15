package com.actiongate.workflow;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** Stable digest used to bind an approval and an idempotent action to the same request. */
public final class ActionRequestFingerprint {
    private ActionRequestFingerprint() {
    }

    public static String refund(TicketInput input) {
        return digest(canonical("create_refund", input.orderId(), input.amount(), input.currency(), null,
                input.idempotencyKey()));
    }

    public static String exchange(TicketInput input) {
        return digest(canonical("create_exchange", input.orderId(), null, input.currency(), input.sku(),
                input.idempotencyKey()));
    }

    private static String canonical(String tool, String orderId, Object amount, String currency, String sku, String key) {
        return String.join("|", tool, value(orderId), value(amount), value(currency), value(sku), value(key));
    }

    private static String value(Object value) {
        if (value == null) {
            return "<null>";
        }
        if (value instanceof java.math.BigDecimal decimal) {
            return decimal.stripTrailingZeros().toPlainString();
        }
        return value.toString();
    }

    private static String digest(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
