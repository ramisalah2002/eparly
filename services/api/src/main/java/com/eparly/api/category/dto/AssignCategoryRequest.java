package com.eparly.api.category.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

@Schema(description = "Demande de modification de la catégorie d'une transaction")
public record AssignCategoryRequest(
        @Schema(description = "ID de la catégorie à assigner, null pour supprimer la catégorie",
                example = "550e8400-e001-0000-0000-000000000002", nullable = true)
        UUID categoryId
) {
}
