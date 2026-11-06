package com.eparly.api.catalogsync.normalization;

import com.eparly.api.offer.domain.OfferSector;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Caracteristiques propres a chaque categorie, stockees dans le JSON characteristics.
 * Les cles mobile et streaming reprennent celles lues par les strategies de comparaison.
 * Ajouter une categorie revient a declarer ici sa specification, sans changer le schema.
 */
@Component
public class CatalogCategorySpecs {

    /**
     * @param required attributs sans lesquels aucune comparaison n'est fondee
     * @param groupBy  attributs devant etre egaux pour que deux offres soient comparables entre elles
     */
    public record CategorySpec(List<String> required, List<String> groupBy) {
    }

    private static final Map<OfferSector, CategorySpec> SPECS = new EnumMap<>(OfferSector.class);

    static {
        SPECS.put(OfferSector.MOBILE_PLAN, new CategorySpec(
                List.of("dataGb", "network5G", "commitmentMonths"), List.of()));
        SPECS.put(OfferSector.INTERNET_BOX, new CategorySpec(
                List.of("technology", "downloadMbps", "commitmentMonths"), List.of("technology")));
        SPECS.put(OfferSector.STREAMING, new CategorySpec(
                List.of("screens", "videoQuality", "ads"), List.of()));
        SPECS.put(OfferSector.MUSIC_STREAMING, new CategorySpec(
                List.of("accounts", "ads"), List.of("accounts")));
        SPECS.put(OfferSector.CLOUD_STORAGE, new CategorySpec(
                List.of("storageGb", "users"), List.of("users")));
        SPECS.put(OfferSector.SOFTWARE_SAAS, new CategorySpec(
                List.of("productFamily", "users", "storageGb"), List.of("productFamily", "users")));
        SPECS.put(OfferSector.GAMING, new CategorySpec(
                List.of("platform", "productFamily"), List.of("platform", "productFamily")));
        SPECS.put(OfferSector.ELECTRICITY, new CategorySpec(
                List.of("tariffOption", "powerKva", "zone", "kwhPricingKnown"),
                List.of("tariffOption", "powerKva", "zone")));
        SPECS.put(OfferSector.GAS, new CategorySpec(
                List.of("tariffOption", "consumptionClass", "zone", "kwhPricingKnown"),
                List.of("tariffOption", "consumptionClass", "zone")));
        SPECS.put(OfferSector.INSURANCE, new CategorySpec(
                List.of("insuranceType", "guarantees", "exclusions", "deductibles"), List.of("insuranceType")));
        SPECS.put(OfferSector.BANKING, new CategorySpec(
                List.of("accountType", "cardType"), List.of("accountType", "cardType")));
        SPECS.put(OfferSector.SPORT_FITNESS, new CategorySpec(
                List.of("activityType", "zone", "commitmentMonths"), List.of("activityType", "zone")));
        SPECS.put(OfferSector.PRESS, new CategorySpec(
                List.of("publication", "format"), List.of("publication", "format")));
        SPECS.put(OfferSector.TRANSPORT, new CategorySpec(
                List.of("network", "zone", "passengerProfile"), List.of("network", "zone", "passengerProfile")));
    }

    /** Une categorie sans specification (OTHER_RECURRING) n'est jamais comparable automatiquement. */
    public CatalogComparability evaluate(OfferSector sector, Map<String, Object> attributes) {
        CategorySpec spec = sector == null ? null : SPECS.get(sector);
        boolean reference = String.valueOf(attributes.get("offerNature")).startsWith("REFERENCE");
        if (spec == null || reference) {
            return new CatalogComparability(CatalogComparability.Status.NOT_COMPARABLE, null, List.of());
        }
        List<String> missing = spec.required().stream()
                .filter(attribute -> !isPresent(attributes.get(attribute))).toList();
        if (!missing.isEmpty()) {
            return new CatalogComparability(CatalogComparability.Status.INSUFFICIENT_ATTRIBUTES, null, missing);
        }
        return new CatalogComparability(CatalogComparability.Status.COMPARABLE_WITHIN_GROUP,
                sector.name() + variantKey(sector, attributes), List.of());
    }

    /** Valeurs discriminantes presentes, utilisees pour ne pas fusionner deux variantes d'un meme nom. */
    public String variantKey(OfferSector sector, Map<String, Object> attributes) {
        CategorySpec spec = sector == null ? null : SPECS.get(sector);
        if (spec == null) return "";
        List<String> parts = new ArrayList<>();
        for (String attribute : spec.groupBy()) {
            Object value = attributes.get(attribute);
            if (isPresent(value)) parts.add(attribute + "=" + value.toString().trim().toLowerCase(Locale.ROOT));
        }
        return parts.isEmpty() ? "" : "|" + String.join("|", parts);
    }

    private static boolean isPresent(Object value) {
        if (value == null) return false;
        if (value instanceof String text) return !text.isBlank();
        if (value instanceof java.util.Collection<?> values) return !values.isEmpty();
        return true;
    }
}
