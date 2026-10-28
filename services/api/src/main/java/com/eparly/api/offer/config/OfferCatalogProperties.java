package com.eparly.api.offer.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
@ConfigurationProperties(prefix = "eparly.offer.catalog")
public class OfferCatalogProperties {

    /**
     * Duree maximale de fraicheur d'une verification d'offre.
     * Au-dela, l'offre est consideree comme STALE et retiree des resultats fiables.
     */
    private Duration freshnessThreshold = Duration.ofDays(30);

    /**
     * Seuil plus court pour les promotions : une verification de promotion est
     * consideree comme stale au-dela de ce delai.
     */
    private Duration promotionFreshnessThreshold = Duration.ofDays(7);

    public Duration getFreshnessThreshold() {
        return freshnessThreshold;
    }

    public void setFreshnessThreshold(Duration freshnessThreshold) {
        this.freshnessThreshold = freshnessThreshold;
    }

    public Duration getPromotionFreshnessThreshold() {
        return promotionFreshnessThreshold;
    }

    public void setPromotionFreshnessThreshold(Duration promotionFreshnessThreshold) {
        this.promotionFreshnessThreshold = promotionFreshnessThreshold;
    }
}
