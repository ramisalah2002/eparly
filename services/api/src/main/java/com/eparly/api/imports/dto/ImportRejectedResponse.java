package com.eparly.api.imports.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Schema(description = "Import refuse (422) : le fichier contient des erreurs bloquantes, aucune transaction n'a ete enregistree. "
        + "Corrigez le fichier ou le mapping puis reessayez.")
public record ImportRejectedResponse(
        @Schema(example = "2026-10-28T09:15:30Z") Instant timestamp,
        @Schema(example = "422") int status,
        @Schema(example = "Unprocessable Content") String error,
        @Schema(example = "Le fichier contient des erreurs bloquantes : aucune transaction n'a ete importee") String message,
        @Schema(example = "/v1/imports") String path,
        @Schema(description = "Import FAILED trace dans l'historique", example = "3f1c2a54-8d7e-4b0a-9a53-0c1d2e3f4a5b") UUID importJobId,
        @Schema(description = "Erreurs bloquantes avec numero de ligne (plafonnees)") List<ImportIssueDto> errors,
        @Schema(description = "Nombre total d'erreurs bloquantes", example = "3") int issueCount,
        @Schema(description = "Vrai si la liste des erreurs a ete tronquee") boolean truncated
) {
}
