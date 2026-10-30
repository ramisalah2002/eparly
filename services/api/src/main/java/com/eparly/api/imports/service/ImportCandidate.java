package com.eparly.api.imports.service;

import java.time.LocalDate;

/**
 * Transaction candidate validee, prete a etre persistee (le client n'en fournit jamais directement).
 */
public record ImportCandidate(
        int line,
        LocalDate bookingDate,
        LocalDate valueDate,
        long amountMinor,
        String currency,
        String rawLabel,
        String normalizedLabel,
        String sourceUid,
        String fingerprint,
        String rawCategory,
        String rawSubcategory
) {
    public ImportCandidate(
            int line,
            LocalDate bookingDate,
            LocalDate valueDate,
            long amountMinor,
            String currency,
            String rawLabel,
            String normalizedLabel,
            String sourceUid,
            String fingerprint
    ) {
        this(line, bookingDate, valueDate, amountMinor, currency, rawLabel, normalizedLabel, sourceUid, fingerprint, null, null);
    }
}
