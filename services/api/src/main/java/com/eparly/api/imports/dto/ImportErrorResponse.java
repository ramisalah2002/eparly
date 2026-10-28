package com.eparly.api.imports.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

@Schema(description = "Erreur API standardisee des imports : meme forme que ApiErrorResponse, avec un code stable en plus")
public record ImportErrorResponse(
        @Schema(example = "2026-10-27T20:00:00Z") Instant timestamp,
        @Schema(example = "400") int status,
        @Schema(example = "Bad Request") String error,
        @Schema(example = "Le fichier n'est pas un UTF-8 valide : selectionnez l'encodage du fichier") String message,
        @Schema(example = "/v1/imports") String path,
        @Schema(description = "Code stable exploitable par le client", example = "INVALID_ENCODING") String code
) {
}
