package com.eparly.api.category.api;

import com.eparly.api.auth.dto.ApiErrorResponse;
import com.eparly.api.auth.service.JwtUserId;
import com.eparly.api.category.dto.CategoryResponse;
import com.eparly.api.category.service.CategoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/v1/categories")
@Tag(name = "Catégories", description = "Consultation des catégories de transactions")
@SecurityRequirement(name = "bearerAuth")
public class CategoryController {

    private final CategoryService categoryService;

    public CategoryController(CategoryService categoryService) {
        this.categoryService = categoryService;
    }

    @GetMapping
    @Operation(summary = "Lister ses catégories",
            description = "Retourne les catégories système ainsi que les catégories privées appartenant à l'utilisateur authentifié.")
    @ApiResponse(responseCode = "200", description = "Liste des catégories visibles",
            content = @Content(schema = @Schema(implementation = CategoryResponse.class)))
    @ApiResponse(responseCode = "401", description = "JWT absent ou invalide",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(responseCode = "403", description = "Compte inactif",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    public List<CategoryResponse> list(@AuthenticationPrincipal Jwt jwt) {
        return categoryService.listVisible(JwtUserId.from(jwt));
    }
}
