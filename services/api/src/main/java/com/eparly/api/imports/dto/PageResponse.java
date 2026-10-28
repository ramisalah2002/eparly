package com.eparly.api.imports.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.data.domain.Page;

import java.util.List;

@Schema(description = "Page de resultats (tri : createdAt decroissant)")
public record PageResponse<T>(
        @Schema(description = "Elements de la page") List<T> content,
        @Schema(description = "Numero de page (commence a 0)", example = "0") int page,
        @Schema(description = "Taille de page effective (plafonnee a 100)", example = "20") int size,
        @Schema(description = "Nombre total d'elements", example = "42") long totalElements,
        @Schema(description = "Nombre total de pages", example = "3") int totalPages
) {

    public static <T> PageResponse<T> from(Page<T> page) {
        return new PageResponse<>(page.getContent(), page.getNumber(), page.getSize(),
                page.getTotalElements(), page.getTotalPages());
    }
}
