package com.eparly.api.imports.service;

import com.eparly.api.imports.mapper.ImportMapping;
import com.eparly.api.imports.parser.CsvCharset;

import java.util.UUID;

/**
 * Requete d'import deja decodee. {@code userId} provient exclusivement du JWT valide, jamais du client.
 *
 * @param fileHash SHA-256 obtenu a la previsualisation (obligatoire a l'import, ignore en previsualisation)
 */
public record ImportCommand(
        UUID userId,
        UUID accountId,
        byte[] data,
        ImportMapping mapping,
        CsvCharset charset,
        Character delimiter,
        String fileHash
) {
}
