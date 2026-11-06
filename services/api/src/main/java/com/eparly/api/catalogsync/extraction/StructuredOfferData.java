package com.eparly.api.catalogsync.extraction;

import com.eparly.api.catalogsync.domain.CatalogPriceKind;

import java.time.LocalDate;
import java.util.Map;

/**
 * Donnees commerciales fournies sous forme structuree par un connecteur. Tout champ absent
 * reste null : un connecteur ne deduit ni frais, ni engagement, ni condition.
 *
 * @param standardAmountMinor prix standard en unites mineures ; prioritaire sur le texte de prix
 * @param priceKind           nature declaree ; deduite a la normalisation si null
 * @param priceComponents     composantes decimales exactes (prix du kWh...), en texte pour ne rien arrondir
 * @param rawPayload          extrait brut borne conserve pour l'audit, exclu de l'empreinte
 */
public record StructuredOfferData(
        String providerName,
        String country,
        Long standardAmountMinor,
        Long promotionAmountMinor,
        LocalDate promotionValidUntil,
        Integer promotionDurationMonths,
        Integer commitmentMonths,
        Long activationFeeMinor,
        CatalogPriceKind priceKind,
        Boolean taxesIncluded,
        Map<String, String> priceComponents,
        LocalDate validFrom,
        LocalDate validUntil,
        Map<String, Object> rawPayload
) {
}
