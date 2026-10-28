package com.eparly.api.imports.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "Resultat de la previsualisation : aucune donnee n'est ecrite en base")
public record ImportPreviewResponse(
        @Schema(description = "SHA-256 du fichier (hexadecimal) a renvoyer tel quel a l'import",
                example = "9f86d081884c7d659a2feaa0c55ad015a3bf4f1b2b0b822cd15d6c15b0f00a08") String fileHash,
        @Schema(description = "Encodage utilise : UTF_8, WINDOWS_1252 ou ISO_8859_1", example = "UTF_8") String detectedCharset,
        @Schema(description = "Vrai si l'encodage n'a pas pu etre determine de facon fiable : l'import exigera un charset explicite") boolean charsetAmbiguous,
        @Schema(description = "Encodages possibles quand charsetAmbiguous est vrai", example = "[\"WINDOWS_1252\",\"ISO_8859_1\"]") List<String> charsetCandidates,
        @Schema(description = "Separateur utilise", example = ";") String detectedDelimiter,
        @Schema(description = "En-tetes detectes", example = "[\"Date operation\",\"Libelle\",\"Montant\"]") List<String> headers,
        @Schema(description = "Mapping propose automatiquement (format de date detecte inclus quand possible)") ImportMappingDto suggestedMapping,
        @Schema(description = "Mapping effectivement applique pendant la previsualisation") ImportMappingDto appliedMapping,
        @Schema(description = "Vrai si le mapping applique est utilisable (colonnes obligatoires trouvees, format de date resolu)") boolean mappingComplete,
        @Schema(description = "Nombre de lignes de donnees non vides", example = "120") int totalRows,
        @Schema(description = "Estimation en lecture seule : lignes qui seraient inserees", example = "118") int wouldInsert,
        @Schema(description = "Estimation en lecture seule : lignes deja presentes dans le compte", example = "2") int wouldSkip,
        @Schema(description = "Vrai si des erreurs bloquantes empecheraient l'import (aucune insertion partielle)") boolean blocking,
        @Schema(description = "Premieres transactions valides") List<ImportSampleRowDto> sample,
        @Schema(description = "Erreurs bloquantes (plafonnees)") List<ImportIssueDto> errors,
        @Schema(description = "Avertissements (plafonnes)") List<ImportIssueDto> warnings,
        @Schema(description = "Nombre total d'erreurs et d'avertissements releves", example = "2") int issueCount,
        @Schema(description = "Vrai si les listes d'erreurs ou d'avertissements ont ete tronquees") boolean truncated
) {
}
