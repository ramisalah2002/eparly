package com.eparly.api.catalogsync.connector;

import com.eparly.api.catalogsync.domain.CatalogSyncSource;

import java.util.List;

/**
 * Lecture d'une source autorisee. Un connecteur ne persiste rien et ne publie rien : il
 * retourne des offres brutes que l'orchestrateur fait passer par la normalisation, la
 * validation et la deduplication. Toute erreur levee n'affecte que la source concernee.
 */
public interface CatalogSourceConnector {

    /** Valeur de catalog_sync_sources.connector_key. */
    String key();

    /** Etat de la configuration, sans aucun appel reseau. */
    Readiness readiness();

    CollectedSourceBatch collect(CatalogSyncSource source, CollectionBudget budget);

    /** Domaines acceptes pour l'URL officielle d'une offre. */
    default List<String> offerDomains(CatalogSyncSource source) {
        return List.of(source.officialDomain());
    }

    /** Le motif ne contient jamais la valeur d'un secret. */
    record Readiness(boolean ready, String reason) {
        public static Readiness isReady() {
            return new Readiness(true, null);
        }

        public static Readiness notConfigured(String reason) {
            return new Readiness(false, reason);
        }
    }
}
