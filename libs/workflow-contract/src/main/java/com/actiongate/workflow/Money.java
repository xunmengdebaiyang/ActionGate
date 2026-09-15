package com.actiongate.workflow;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/** Monetary value with an explicit ISO-4217 currency and two-decimal minor-unit precision. */
public record Money(long minorUnits, String currency) {
    public static final int SCALE = 2;

    public Money {
        if (currency == null || !currency.matches("[A-Z]{3}")) {
            throw new IllegalArgumentException("Currency must be a three-letter uppercase ISO-4217 code");
        }
        if (minorUnits < 0) {
            throw new IllegalArgumentException("Money cannot be negative");
        }
    }

    public static Money of(BigDecimal units, String currency) {
        Objects.requireNonNull(units, "amount");
        if (units.scale() > SCALE || units.signum() < 0) {
            throw new IllegalArgumentException("Amount must be non-negative with at most two decimal places");
        }
        try {
            return new Money(units.movePointRight(SCALE).longValueExact(), currency);
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException("Amount exceeds supported precision", exception);
        }
    }

    public BigDecimal units() {
        return BigDecimal.valueOf(minorUnits, SCALE).setScale(SCALE, RoundingMode.UNNECESSARY);
    }

    public Money add(Money other) {
        requireCurrency(other);
        return new Money(Math.addExact(minorUnits, other.minorUnits), currency);
    }

    public boolean isGreaterThan(Money other) {
        requireCurrency(other);
        return minorUnits > other.minorUnits;
    }

    private void requireCurrency(Money other) {
        if (other == null || !currency.equals(other.currency)) {
            throw new IllegalArgumentException("Currency mismatch");
        }
    }
}
