package com.eparly.api.savings.verification.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "Liste paginée des transactions candidates pour vérification")
public record VerificationCandidatesResponse(
        @Schema(description = "Liste des transactions candidates")
        List<CandidateTransactionResponse> items,

        @Schema(description = "Numéro de page courante (0-indexée)")
        int page,

        @Schema(description = "Taille de page demandée")
        int size,

        @Schema(description = "Nombre total de transactions candidates")
        long totalElements,

        @Schema(description = "Nombre total de pages")
        int totalPages
) {
}
