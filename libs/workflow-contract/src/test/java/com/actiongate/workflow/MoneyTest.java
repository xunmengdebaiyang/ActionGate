package com.actiongate.workflow;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MoneyTest {
    @Test
    void storesAmountsAsMinorUnitsWithAnExplicitCurrency() {
        var money = Money.of(new BigDecimal("199.00"), "CNY");
        assertEquals(19900, money.minorUnits());
        assertEquals(new BigDecimal("199.00"), money.units());
        assertEquals("CNY", money.currency());
    }

    @Test
    void rejectsAmbiguousPrecisionNegativeValuesAndCurrencyMismatch() {
        assertThrows(IllegalArgumentException.class, () -> Money.of(new BigDecimal("1.001"), "CNY"));
        assertThrows(IllegalArgumentException.class, () -> Money.of(new BigDecimal("-1.00"), "CNY"));
        var cny = Money.of(new BigDecimal("1.00"), "CNY");
        var usd = Money.of(new BigDecimal("1.00"), "USD");
        assertThrows(IllegalArgumentException.class, () -> cny.add(usd));
    }
}
