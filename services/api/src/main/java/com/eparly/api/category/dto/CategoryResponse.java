package com.eparly.api.category.dto;

import com.eparly.api.category.domain.Category;
import com.eparly.api.category.domain.CategoryType;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

@Schema(description = "Catégorie de transaction (système ou privée)")
public record CategoryResponse(
        @Schema(example = "550e8400-e001-0000-0000-000000000001") UUID id,
        @Schema(example = "Courses") String name,
        @Schema(example = "EXPENSE", allowableValues = {"EXPENSE", "INCOME", "BOTH"}) CategoryType categoryType,
        @Schema(description = "Vrai pour une catégorie système, faux pour une catégorie privée", example = "true") boolean system,
        @Schema(description = "ID de la catégorie parente, null si racine", nullable = true, example = "550e8400-e001-0000-0000-000000000001") UUID parentId
) {
    public static CategoryResponse from(Category category) {
        return new CategoryResponse(
                category.getId(),
                category.getName(),
                category.getCategoryType(),
                category.isSystemFlag(),
                category.getParent() != null ? category.getParent().getId() : null
        );
    }
}
