package com.eparly.api.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

@Schema(description = "Erreur API standardisée")
public record ApiErrorResponse(
        @Schema(example = "2026-10-27T20:00:00Z") Instant timestamp,
        @Schema(example = "400") int status,
        @Schema(example = "Bad Request") String error,
        @Schema(example = "The request is invalid") String message,
        @Schema(example = "/v1/auth/register") String path
) {
}
