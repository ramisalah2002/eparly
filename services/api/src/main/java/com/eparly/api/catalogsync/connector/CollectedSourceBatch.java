package com.eparly.api.catalogsync.connector;

import com.eparly.api.catalogsync.extraction.RawCatalogOffer;

import java.util.List;

/**
 * Resultat de lecture d'une source.
 *
 * @param complete vrai seulement si la source a ete lue integralement : une offre absente d'une
 *                 lecture partielle, vide ou non modifiee n'est jamais consideree comme non retournee
 */
public record CollectedSourceBatch(
        List<RawCatalogOffer> offers,
        List<String> unsupportedPages,
        int pagesFetched,
        boolean notModified,
        boolean complete,
        String etag,
        String lastModified
) {
    public static CollectedSourceBatch notModified(String etag, String lastModified) {
        return new CollectedSourceBatch(List.of(), List.of(), 0, true, false, etag, lastModified);
    }
}
