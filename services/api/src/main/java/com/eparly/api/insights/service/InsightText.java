package com.eparly.api.insights.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;

/** Mise en forme des textes. Une future couche generative pourrait reformuler ces faits, sans etre necessaire. */
final class InsightText {
    private InsightText() {
    }

    static String euros(long minor) {
        BigDecimal value = BigDecimal.valueOf(minor).movePointLeft(2);
        if (value.stripTrailingZeros().scale() <= 0) return value.setScale(0, RoundingMode.UNNECESSARY).toPlainString() + " €";
        return String.format(Locale.FRANCE, "%.2f €", value);
    }

    static int percent(long part, long whole) {
        return whole == 0 ? 0 : (int) Math.round(part * 100.0 / whole);
    }
}
