package com.eparly.api.imports.parser;

import java.math.BigDecimal;
import java.util.Currency;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Conversion exacte d'un montant texte en unites mineures (centimes) via BigDecimal.
 * Aucun float/double. Les valeurs ambigues, hors capacite ou incompatibles avec la devise sont refusees.
 */
public final class AmountParser {

    private static final Pattern DIGITS = Pattern.compile("^\\d+$");
    private static final Pattern BODY = Pattern.compile("^\\d[\\d .,]*$");
    private static final Pattern GROUPED = Pattern.compile("^\\d{1,3}([ .,])\\d{3}(?:\\1\\d{3})*$");

    private static final String[][] CURRENCY_SYMBOLS = {
            {"EUR", "EUR"}, {"USD", "USD"}, {"GBP", "GBP"}, {"CHF", "CHF"},
            {"\u20AC", "EUR"}, {"$", "USD"}, {"\u00A3", "GBP"}
    };

    private AmountParser() {
    }

    /**
     * @param raw      valeur lue dans le CSV
     * @param format   convention de notation
     * @param currency devise du compte (ISO 4217) ; un symbole monetaire different est refuse
     * @return montant signe en unites mineures (centimes pour l'euro)
     */
    public static long parse(String raw, AmountFormat format, String currency) throws ValueParseException {
        if (raw == null) {
            throw invalid();
        }
        AmountFormat effective = format == null ? AmountFormat.AUTO : format;
        String s = normalizeSpaces(raw).trim();
        if (s.isEmpty()) {
            throw invalid();
        }

        boolean negative = false;
        boolean signSeen = false;
        boolean symbolSeen = false;
        boolean changed = true;
        while (changed) {
            changed = false;
            s = s.trim();
            if (!signSeen && !s.isEmpty()) {
                char c = s.charAt(0);
                if (c == '+' || c == '-' || c == '\u2212') {
                    negative = c != '+';
                    s = s.substring(1);
                    signSeen = true;
                    changed = true;
                    continue;
                }
            }
            if (!symbolSeen) {
                for (String[] symbol : CURRENCY_SYMBOLS) {
                    String token = symbol[0];
                    boolean prefix = s.regionMatches(true, 0, token, 0, token.length());
                    boolean suffix = s.length() >= token.length()
                            && s.regionMatches(true, s.length() - token.length(), token, 0, token.length());
                    if (prefix || suffix) {
                        if (!symbol[1].equalsIgnoreCase(currency)) {
                            throw new ValueParseException("CURRENCY_MISMATCH",
                                    "Le symbole monetaire ne correspond pas a la devise du compte");
                        }
                        s = prefix ? s.substring(token.length()) : s.substring(0, s.length() - token.length());
                        symbolSeen = true;
                        changed = true;
                        break;
                    }
                }
            }
        }

        s = s.trim();
        if (!BODY.matcher(s).matches() || !Character.isDigit(s.charAt(s.length() - 1))) {
            throw invalid();
        }

        String[] parts = split(s, effective);
        String intDigits = parts[0];
        String fraction = parts[1];

        int fractionDigits = fractionDigits(currency);
        if (fraction.length() > fractionDigits) {
            throw new ValueParseException("TOO_MANY_DECIMALS",
                    "Trop de decimales pour la devise du compte");
        }
        BigDecimal value = new BigDecimal(fraction.isEmpty() ? intDigits : intDigits + "." + fraction)
                .setScale(fractionDigits);
        java.math.BigInteger minor = value.unscaledValue();
        if (negative) {
            minor = minor.negate();
        }
        try {
            return minor.longValueExact();
        } catch (ArithmeticException e) {
            throw new ValueParseException("AMOUNT_OUT_OF_RANGE", "Montant hors des limites supportees");
        }
    }

    /** @return {partie entiere sans separateur, partie decimale} */
    private static String[] split(String body, AmountFormat format) throws ValueParseException {
        int dots = count(body, '.');
        int commas = count(body, ',');
        boolean hasSpace = body.indexOf(' ') >= 0;
        char decimal = 0;
        String groupers;

        switch (format) {
            case FR -> {
                if (dots > 0 && commas == 0) {
                    throw new ValueParseException("INVALID_AMOUNT",
                            "Format FR : le point n'est pas un separateur decimal");
                }
                decimal = ',';
                groupers = " .";
            }
            case EN -> {
                decimal = '.';
                groupers = " ,";
            }
            default -> {
                groupers = " .,";
                if (dots > 0 && commas > 0) {
                    decimal = body.lastIndexOf('.') > body.lastIndexOf(',') ? '.' : ',';
                } else if (hasSpace) {
                    decimal = dots == 1 ? '.' : commas == 1 ? ',' : 0;
                } else if (dots + commas == 1) {
                    char sep = dots == 1 ? '.' : ',';
                    int index = body.indexOf(sep);
                    int after = body.length() - index - 1;
                    String before = body.substring(0, index);
                    if (after == 3 && before.length() <= 3 && !before.startsWith("0")) {
                        throw new ValueParseException("AMBIGUOUS_AMOUNT",
                                "Montant ambigu : precisez le format (FR ou EN) pour lever l'ambiguite");
                    }
                    decimal = sep;
                }
            }
        }

        if (decimal != 0 && count(body, decimal) > 1) {
            throw invalid();
        }
        int decimalIndex = decimal == 0 ? -1 : body.indexOf(decimal);
        String intPart = decimalIndex < 0 ? body : body.substring(0, decimalIndex);
        String fraction = decimalIndex < 0 ? "" : body.substring(decimalIndex + 1);
        if (decimalIndex >= 0 && !DIGITS.matcher(fraction).matches()) {
            throw invalid();
        }

        String intDigits;
        if (DIGITS.matcher(intPart).matches()) {
            intDigits = intPart;
        } else {
            Matcher matcher = GROUPED.matcher(intPart);
            if (!matcher.matches()) {
                throw invalid();
            }
            char grouper = matcher.group(1).charAt(0);
            if (groupers.indexOf(grouper) < 0 || grouper == decimal) {
                throw invalid();
            }
            if (format == AmountFormat.FR && grouper == '.' && decimalIndex < 0) {
                throw new ValueParseException("AMBIGUOUS_AMOUNT",
                        "Montant ambigu : le point n'est pas un separateur decimal en format FR");
            }
            intDigits = intPart.replace(String.valueOf(grouper), "");
        }
        return new String[]{intDigits, fraction};
    }

    private static int fractionDigits(String currency) {
        try {
            int digits = Currency.getInstance(currency.toUpperCase(Locale.ROOT)).getDefaultFractionDigits();
            return digits < 0 ? 2 : digits;
        } catch (IllegalArgumentException e) {
            return 2;
        }
    }

    private static int count(String s, char c) {
        int n = 0;
        for (int i = 0; i < s.length(); i++) {
            if (s.charAt(i) == c) {
                n++;
            }
        }
        return n;
    }

    private static String normalizeSpaces(String s) {
        return s.replace('\u00A0', ' ').replace('\u202F', ' ').replace('\u2009', ' ').replace('\u2007', ' ');
    }

    private static ValueParseException invalid() {
        return new ValueParseException("INVALID_AMOUNT", "Montant non reconnu");
    }
}
