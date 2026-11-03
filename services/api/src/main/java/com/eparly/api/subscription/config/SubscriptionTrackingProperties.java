package com.eparly.api.subscription.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.context.annotation.Configuration;

/**
 * Seuils du suivi des abonnements. Montants en centimes.
 *
 * @param chargeGraceDays delai de comptabilisation toleré apres la date de fin avant de signaler un prelevement
 */
@ConfigurationProperties(prefix = "eparly.subscriptions")
public record SubscriptionTrackingProperties(
        @DefaultValue("5") int chargeGraceDays,
        @DefaultValue("2") int minObservedCycles,
        @DefaultValue("5") int priceIncreasePercent,
        @DefaultValue("100") long minPriceIncreaseMinor,
        @DefaultValue("30000") long costlyAnnualMinor,
        @DefaultValue("30") int renewalAlertDays,
        @DefaultValue("6000") long importantRenewalMinor,
        @DefaultValue("14") int plannedStaleDays,
        @DefaultValue("30") int requestedStaleDays
) {
    @Configuration
    @EnableConfigurationProperties(SubscriptionTrackingProperties.class)
    static class Registration {
    }
}
