package com.eparly.api.savings.action.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "Réponse pagée de liste d'actions d'économie")
public record SavingsActionListResponse(

        @Schema(description = "Actions de la page") List<SavingsActionResponse> content,
        @Schema(description = "Numéro de page (commence à 0)", example = "0") int page,
        @Schema(description = "Taille de page effective", example = "20") int size,
        @Schema(description = "Nombre total d'éléments", example = "42") long totalElements,
        @Schema(description = "Nombre total de pages", example = "3") int totalPages
) {
}
