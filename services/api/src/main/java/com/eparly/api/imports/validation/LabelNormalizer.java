package com.eparly.api.imports.validation;

import java.text.Normalizer;
import java.util.Locale;

/**
 * Libelle normalise utilise pour l'empreinte de deduplication : sans accents, en majuscules,
 * espaces reduits. Robuste aux differences d'encodage ou d'espacement entre deux exports.
 */
public final class LabelNormalizer {

    private LabelNormalizer() {
    }

    public static String normalize(String raw) {
        if (raw == null) {
            return "";
        }
        String decomposed = Normalizer.normalize(raw, Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
        String normalized = decomposed.toUpperCase(Locale.ROOT)
                .replaceAll("[\\s\\u00A0\\u202F\\u2007]+", " ")
                .trim();
        return normalized.isEmpty() ? raw.trim() : normalized;
    }
}
