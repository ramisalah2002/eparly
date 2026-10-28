package com.eparly.api.offer.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "Reponse paginee de liste d'offres commerciales")
public record OfferListResponse(
        @Schema(description = "Elements de la page") List<OfferListItem> content,
        @Schema(description = "Numero de page (commence a 0)", example = "0") int page,
        @Schema(description = "Taille de page effective", example = "20") int size,
        @Schema(description = "Nombre total d'elements", example = "42") long totalElements,
        @Schema(description = "Nombre total de pages", example = "3") int totalPages
) {
}
