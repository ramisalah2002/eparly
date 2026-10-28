package com.eparly.api.imports.parser;

/**
 * Detecte le separateur (point-virgule, virgule ou tabulation) sur la premiere ligne non vide,
 * en ignorant les separateurs situes dans des champs entre guillemets.
 */
public final class DelimiterDetector {

    private static final char[] CANDIDATES = {';', ',', '\t'};

    private DelimiterDetector() {
    }

    public static char detect(String text) {
        int[] counts = new int[CANDIDATES.length];
        boolean inQuotes = false;
        boolean seenContent = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '"') {
                inQuotes = !inQuotes;
                seenContent = true;
            } else if (!inQuotes && (c == '\n' || c == '\r')) {
                if (seenContent) {
                    break;
                }
            } else if (!inQuotes) {
                if (!Character.isWhitespace(c)) {
                    seenContent = true;
                }
                for (int k = 0; k < CANDIDATES.length; k++) {
                    if (c == CANDIDATES[k]) {
                        counts[k]++;
                    }
                }
            }
        }
        int best = 0;
        for (int k = 1; k < CANDIDATES.length; k++) {
            if (counts[k] > counts[best]) {
                best = k;
            }
        }
        return CANDIDATES[best];
    }

    /** Convertit un parametre utilisateur (";", ",", "\t", "SEMICOLON", "COMMA", "TAB"). */
    public static Character parse(String value) {
        if (value == null || value.isEmpty()) {
            return null;
        }
        return switch (value.trim().toUpperCase()) {
            case ";", "SEMICOLON" -> ';';
            case ",", "COMMA" -> ',';
            case "\t", "TAB" -> '\t';
            default -> throw new IllegalArgumentException("Unsupported delimiter");
        };
    }
}
