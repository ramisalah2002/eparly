package com.eparly.api.insights.api;

import com.eparly.api.auth.dto.ApiErrorResponse;
import com.eparly.api.auth.exception.ApiException;
import com.eparly.api.auth.service.JwtUserId;
import com.eparly.api.category.domain.CategoryType;
import com.eparly.api.category.service.CategoryService;
import com.eparly.api.insights.domain.InsightModels.Budget;
import com.eparly.api.insights.dto.InsightDtos.BudgetRequest;
import com.eparly.api.insights.dto.InsightDtos.BudgetResponse;
import com.eparly.api.insights.repository.InsightsRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/** Limites mensuelles saisies par l'utilisateur ; le moteur de recommandations ne les modifie jamais. */
@RestController
@RequestMapping("/v1/budgets")
@Tag(name = "Budgets", description = "Limite mensuelle par catégorie, en centimes d'euros")
@SecurityRequirement(name = "bearerAuth")
public class BudgetController {
    private final InsightsRepository repository;
    private final CategoryService categoryService;

    public BudgetController(InsightsRepository repository, CategoryService categoryService) {
        this.repository = repository;
        this.categoryService = categoryService;
    }

    @GetMapping
    @Operation(summary = "Lister ses budgets")
    public List<BudgetResponse> list(@AuthenticationPrincipal Jwt jwt) {
        return repository.budgets(JwtUserId.from(jwt)).stream().map(BudgetController::response).toList();
    }

    @PutMapping("/{categoryId}")
    @Operation(summary = "Créer ou modifier le budget d'une catégorie")
    @ApiResponse(responseCode = "404", description = "Catégorie non visible par l'utilisateur",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    public BudgetResponse upsert(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID categoryId,
                                 @Valid @RequestBody BudgetRequest request) {
        UUID userId = JwtUserId.from(jwt);
        if (categoryService.getVisibleCategory(categoryId, userId).getCategoryType() == CategoryType.INCOME) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "A budget requires an expense category");
        }
        return response(repository.upsertBudget(userId, categoryId, request.monthlyLimitMinor()));
    }

    @DeleteMapping("/{categoryId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Supprimer le budget d'une catégorie")
    public void delete(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID categoryId) {
        if (repository.deleteBudget(JwtUserId.from(jwt), categoryId) == 0) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Budget not found");
        }
    }

    private static BudgetResponse response(Budget budget) {
        return new BudgetResponse(budget.id(), budget.categoryId(), budget.categoryName(), budget.monthlyLimitMinor(), "EUR");
    }
}
