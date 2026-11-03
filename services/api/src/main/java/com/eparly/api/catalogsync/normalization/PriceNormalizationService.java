package com.eparly.api.catalogsync.normalization;

import com.eparly.api.offer.domain.OfferBillingPeriod;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class PriceNormalizationService {
    private static final Pattern AMOUNT = Pattern.compile(
            "(?<![\\d])(?:\\d{1,3}(?:[ .\\u00a0]\\d{3})+|\\d+)(?:[,.]\\d+)?");

    public PriceValue normalize(String text, String explicitCurrency, String explicitPeriod) {
        if (text == null || text.isBlank()) return new PriceValue(null, normalizeCurrency(explicitCurrency),
                normalizePeriod(explicitPeriod), false, false);

        Matcher matcher = AMOUNT.matcher(text);
        if (!matcher.find()) {
            return new PriceValue(isExplicitlyFree(text) ? Long.valueOf(0L) : null,
                    normalizeCurrency(explicitCurrency), normalizePeriod(explicitPeriod),
                    isStartingPrice(text), false);
        }
        // Le premier montant accompagne d'une devise prime : "1 mois offert puis 9,99 EUR" vaut 9,99.
        String firstAmount = matcher.group();
        int firstStart = matcher.start();
        do {
            if (hasCurrencyMark(text, matcher)) {
                firstAmount = matcher.group();
                firstStart = matcher.start();
                break;
            }
        } while (matcher.find());

        String amountText = firstAmount.replace(" ", "").replace("\u00a0", "").replace(",", ".");
        Long amountMinor;
        try {
            // Plus de deux decimales ou montant signe : valeur ambigue, jamais tronquee ni arrondie.
            amountMinor = firstStart > 0 && text.charAt(firstStart - 1) == '-' ? null
                    : new BigDecimal(amountText).movePointRight(2)
                    .setScale(0, RoundingMode.UNNECESSARY).longValueExact();
        } catch (ArithmeticException | NumberFormatException exception) {
            amountMinor = null;
        }

        String currency = normalizeCurrency(explicitCurrency);
        if (currency == null && text.contains("€")) currency = "EUR";
        OfferBillingPeriod billingPeriod = normalizePeriod(explicitPeriod);
        if (billingPeriod == null) billingPeriod = inferPeriod(text);
        return new PriceValue(amountMinor, currency, billingPeriod, isStartingPrice(text),
                countCurrencyMarkedAmounts(text) > 1);
    }

    private static String normalizeCurrency(String currency) {
        if (currency == null || currency.isBlank()) return null;
        String normalized = currency.trim().toUpperCase(Locale.ROOT);
        if (normalized.equals("€")) return "EUR";
        return normalized.matches("[A-Z]{3}") ? normalized : null;
    }

    private static OfferBillingPeriod normalizePeriod(String period) {
        if (period == null || period.isBlank()) return null;
        try {
            OfferBillingPeriod billingPeriod = OfferBillingPeriod.valueOf(period.trim().toUpperCase(Locale.ROOT));
            return billingPeriod == OfferBillingPeriod.UNKNOWN ? null : billingPeriod;
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private static OfferBillingPeriod inferPeriod(String text) {
        String normalized = text.toLowerCase(Locale.ROOT);
        if (normalized.matches("(?s).*(/\\s*mois|par mois|mensuel|mensuelle|monthly|/month).*")) {
            return OfferBillingPeriod.MONTHLY;
        }
        if (normalized.matches("(?s).*(/\\s*an|par an|annuel|annuelle|yearly|annually|/year).*")) {
            return OfferBillingPeriod.YEARLY;
        }
        if (normalized.matches("(?s).*(/\\s*semaine|par semaine|hebdomadaire|weekly|/week).*")) {
            return OfferBillingPeriod.WEEKLY;
        }
        if (normalized.matches("(?s).*(/\\s*trimestre|par trimestre|trimestriel|quarterly).*")) {
            return OfferBillingPeriod.QUARTERLY;
        }
        return null;
    }

    private static boolean isStartingPrice(String text) {
        String normalized = text.toLowerCase(Locale.ROOT);
        return normalized.contains("à partir de") || normalized.contains("a partir de")
                || normalized.contains("from ") || normalized.contains("starting at");
    }

    private static boolean isExplicitlyFree(String text) {
        return text.trim().toLowerCase(Locale.ROOT).matches("(?s)(gratuit|gratuite|free)\\b.*");
    }

    private static int countCurrencyMarkedAmounts(String text) {
        Matcher matcher = AMOUNT.matcher(text);
        int count = 0;
        while (matcher.find()) {
            if (hasCurrencyMark(text, matcher)) count++;
        }
        return count;
    }

    private static boolean hasCurrencyMark(String text, Matcher matcher) {
        int start = Math.max(0, matcher.start() - 4);
        int end = Math.min(text.length(), matcher.end() + 5);
        String context = text.substring(start, end).toUpperCase(Locale.ROOT);
        return context.contains("€") || context.matches("(?s).*\\b(?:EUR|USD|GBP|CHF)\\b.*");
    }

    public record PriceValue(Long amountMinor, String currency, OfferBillingPeriod billingPeriod,
                             boolean startingAtPrice, boolean ambiguous) {
    }
}
