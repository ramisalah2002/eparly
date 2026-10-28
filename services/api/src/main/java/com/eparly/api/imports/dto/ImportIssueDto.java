package com.eparly.api.imports.dto;

import com.eparly.api.imports.validation.ImportIssue;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Erreur ou avertissement releve dans le fichier. Ne contient jamais de valeur du fichier.")
public record ImportIssueDto(
        @Schema(description = "Numero de ligne du fichier (1 = premiere ligne), 0 si l'anomalie concerne tout le fichier ou le mapping", example = "14") int line,
        @Schema(description = "Nom de la colonne concernee", example = "Montant", nullable = true) String column,
        @Schema(description = "Code stable", example = "INVALID_AMOUNT") String code,
        @Schema(description = "Description lisible", example = "Montant non reconnu") String message
) {

    public static ImportIssueDto from(ImportIssue issue) {
        return new ImportIssueDto(issue.line(), issue.column(), issue.code(), issue.message());
    }
}
