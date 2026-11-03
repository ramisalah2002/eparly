package com.eparly.api.catalogsync.normalization;

import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Isole le tarif d'une observation pour distinguer changement de prix, de promotion ou de details. */
@Component
public class CatalogPriceChangeDetector {

    public enum Change {
        NONE,
        PRICE_CHANGED,
        PROMOTION_CHANGED
    }

    private static final String[] PRICE_FIELDS =
            {"amountMinor", "currency", "billingPeriod", "priceKind", "activationFeeMinor", "priceComponents"};
    private static final String[] PROMOTION_FIELDS =
            {"promotionAmountMinor", "promotionValidUntil", "promotionDurationMonths", "promotionExpired",
                    "promotionText"};

    /** Instantane du tarif. Une valeur inconnue reste null et se distingue ainsi d'un montant nul. */
    public Map<String, Object> snapshot(Map<String, Object> normalized) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        for (String field : PRICE_FIELDS) snapshot.put(field, normalized.get(field));
        for (String field : PROMOTION_FIELDS) snapshot.put(field, normalized.get(field));
        return snapshot;
    }

    public Change detect(Map<String, Object> previous, Map<String, Object> current) {
        for (String field : PRICE_FIELDS) {
            if (!sameValue(comparable(field, previous.get(field)), comparable(field, current.get(field)))) {
                return Change.PRICE_CHANGED;
            }
        }
        for (String field : PROMOTION_FIELDS) {
            if (!sameValue(previous.get(field), current.get(field))) return Change.PROMOTION_CHANGED;
        }
        return Change.NONE;
    }

    /** L'apparition d'une promotion change la nature du prix sans changer le prix standard. */
    private static Object comparable(String field, Object value) {
        return "priceKind".equals(field) && "PROMOTIONAL".equals(value) ? "FIXED" : value;
    }

    private static boolean sameValue(Object left, Object right) {
        if (left instanceof Number && right instanceof Number) {
            return ((Number) left).longValue() == ((Number) right).longValue();
        }
        return Objects.equals(left, right);
    }
}
