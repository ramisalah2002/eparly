package com.eparly.api.imports.parser;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Conversion stricte des dates en {@link LocalDate}. Jamais d'inversion silencieuse jour/mois :
 * la detection automatique n'essaie que des formats ou le jour precede le mois (ou l'annee en tete, ISO).
 * Les formats mois-jour ne sont utilises que s'ils sont demandes explicitement.
 */
public final class DateParser {

    public static final String AUTO = "AUTO";

    /** Formats essayes en detection automatique (jour d'abord, ou annee en tete). */
    public static final List<String> AUTO_PATTERNS = List.of(
            "dd/MM/yyyy", "dd-MM-yyyy", "dd.MM.yyyy", "yyyy-MM-dd", "yyyy/MM/dd");

    /** Liste blanche des patterns acceptes dans le mapping. */
    public static final List<String> SUPPORTED_PATTERNS = List.of(
            "dd/MM/yyyy", "dd-MM-yyyy", "dd.MM.yyyy", "yyyy-MM-dd", "yyyy/MM/dd",
            "MM/dd/yyyy", "MM-dd-yyyy", "yyyyMMdd");

    private static final int MIN_YEAR = 1900;
    private static final int MAX_YEAR = 2200;

    private static final Map<String, DateTimeFormatter> FORMATTERS = new LinkedHashMap<>();

    static {
        for (String pattern : SUPPORTED_PATTERNS) {
            FORMATTERS.put(pattern, DateTimeFormatter
                    .ofPattern(pattern.replace("yyyy", "uuuu"), Locale.ROOT)
                    .withResolverStyle(ResolverStyle.STRICT));
        }
    }

    private DateParser() {
    }

    public static boolean isSupported(String pattern) {
        return pattern != null && FORMATTERS.containsKey(pattern);
    }

    public static LocalDate parse(String raw, String pattern) throws ValueParseException {
        DateTimeFormatter formatter = pattern == null ? null : FORMATTERS.get(pattern);
        if (formatter == null) {
            throw new ValueParseException("UNSUPPORTED_DATE_FORMAT", "Format de date non supporte");
        }
        if (raw == null || raw.isBlank()) {
            throw new ValueParseException("INVALID_DATE", "Date non reconnue");
        }
        try {
            LocalDate date = LocalDate.parse(raw.trim(), formatter);
            if (date.getYear() < MIN_YEAR || date.getYear() > MAX_YEAR) {
                throw new ValueParseException("INVALID_DATE", "Date hors plage");
            }
            return date;
        } catch (DateTimeParseException e) {
            throw new ValueParseException("INVALID_DATE", "Date non reconnue");
        }
    }

    /**
     * Detecte le format jour-premier qui convertit le plus grand nombre d'echantillons non vides.
     * A egalite, le premier de la liste l'emporte. Les valeurs invalides restantes seront signalees ligne par ligne.
     */
    public static Optional<String> detect(Collection<String> samples) {
        String best = null;
        int bestCount = 0;
        for (String pattern : AUTO_PATTERNS) {
            int count = 0;
            for (String sample : samples) {
                if (sample == null || sample.isBlank()) {
                    continue;
                }
                try {
                    parse(sample, pattern);
                    count++;
                } catch (ValueParseException ignored) {
                    // echantillon non conforme a ce format
                }
            }
            if (count > bestCount) {
                best = pattern;
                bestCount = count;
            }
        }
        return Optional.ofNullable(best);
    }
}
