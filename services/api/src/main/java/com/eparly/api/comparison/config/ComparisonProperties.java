package com.eparly.api.comparison.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
@ConfigurationProperties(prefix = "eparly.comparison")
public class ComparisonProperties {

    /**
     * Seuil de fraicheur des offres : une verification trop ancienne rend l'offre ineligible.
     */
    private Duration freshnessThreshold = Duration.ofDays(30);

    /**
     * Seuil plus court pour les promotions.
     */
    private Duration promotionFreshnessThreshold = Duration.ofDays(7);

    /**
     * Score heuristique maximum.
     */
    private int maxHeuristicScore = 100;

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

    public int getMaxHeuristicScore() {
        return maxHeuristicScore;
    }

    public void setMaxHeuristicScore(int maxHeuristicScore) {
        this.maxHeuristicScore = maxHeuristicScore;
    }
}
