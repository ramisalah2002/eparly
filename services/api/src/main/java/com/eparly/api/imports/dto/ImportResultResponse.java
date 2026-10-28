package com.eparly.api.imports.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "Resultat d'un import confirme")
public record ImportResultResponse(
        @Schema(description = "Import enregistre") ImportJobResponse importJob,
        @Schema(description = "Avertissements non bloquants releves pendant l'import (plafonnes)") List<ImportIssueDto> warnings
) {
}
