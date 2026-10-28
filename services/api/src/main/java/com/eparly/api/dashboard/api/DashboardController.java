package com.eparly.api.dashboard.api;

import com.eparly.api.auth.dto.ApiErrorResponse;
import com.eparly.api.auth.exception.ApiException;
import com.eparly.api.auth.service.JwtUserId;
import com.eparly.api.dashboard.dto.DashboardSummaryResponse;
import com.eparly.api.dashboard.dto.MonthlyTrendResponse;
import com.eparly.api.dashboard.dto.SpendingByCategoryResponse;
import com.eparly.api.dashboard.service.DashboardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/v1/dashboard")
@Tag(name = "Dashboard", description = "Statistiques financières agrégées à partir des transactions importées")
@SecurityRequirement(name = "bearerAuth")
public class DashboardController {

    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping("/summary")
    @Operation(summary = "Résumé financier",
            description = "Retourne les agrégats financiers sur une période donnée. "
                    + "Les données proviennent d'imports CSV potentiellement incomplets "
                    + "et ne représentent pas un solde bancaire réel.")
    @ApiResponse(responseCode = "200", description = "Résumé statistique",
            content = @Content(schema = @Schema(implementation = DashboardSummaryResponse.class)))
    @ApiResponse(responseCode = "400", description = "Dates invalides",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(responseCode = "401", description = "JWT absent ou invalide",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(responseCode = "404", description = "Compte inconnu ou appartenant à un autre utilisateur",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    public DashboardSummaryResponse summary(
            @AuthenticationPrincipal Jwt jwt,
            @Parameter(description = "Date de début incluse (ISO-8601)", example = "2025-01-01")
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @Parameter(description = "Date de fin incluse (ISO-8601)", example = "2025-12-31")
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @Parameter(description = "Filtre optionnel par compte appartenant à l'utilisateur")
            @RequestParam(required = false) UUID accountId
    ) {
        if (startDate.isAfter(endDate)) {
            throw new ApiException(org.springframework.http.HttpStatus.BAD_REQUEST, "startDate must be before or equal to endDate");
        }
        return dashboardService.getSummary(JwtUserId.from(jwt), startDate, endDate, accountId);
    }

    @GetMapping("/spending-by-category")
    @Operation(summary = "Répartition des dépenses par catégorie",
            description = "Retourne la répartition des dépenses (EXPENSE + FEE) par catégorie. "
                    + "Inclut un groupe 'Non catégorisé' pour les dépenses sans catégorie.")
    @ApiResponse(responseCode = "200", description = "Liste des catégories avec montants et pourcentages",
            content = @Content(schema = @Schema(
                    implementation = SpendingByCategoryResponse.class,
                    type = "array")))
    @ApiResponse(responseCode = "400", description = "Dates invalides",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(responseCode = "401", description = "JWT absent ou invalide",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(responseCode = "404", description = "Compte inconnu ou appartenant à un autre utilisateur",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    public List<SpendingByCategoryResponse> spendingByCategory(
            @AuthenticationPrincipal Jwt jwt,
            @Parameter(description = "Date de début incluse (ISO-8601)", example = "2025-01-01")
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @Parameter(description = "Date de fin incluse (ISO-8601)", example = "2025-12-31")
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @Parameter(description = "Filtre optionnel par compte appartenant à l'utilisateur")
            @RequestParam(required = false) UUID accountId
    ) {
        if (startDate.isAfter(endDate)) {
            throw new ApiException(org.springframework.http.HttpStatus.BAD_REQUEST, "startDate must be before or equal to endDate");
        }
        return dashboardService.getSpendingByCategory(JwtUserId.from(jwt), startDate, endDate, accountId);
    }

    @GetMapping("/monthly-trend")
    @Operation(summary = "Évolution mensuelle",
            description = "Retourne l'évolution des dépenses et revenus sur les N derniers mois. "
                    + "Les mois sans transactions sont inclus avec des indicateurs nuls.")
    @ApiResponse(responseCode = "200", description = "Liste des mois avec agrégats",
            content = @Content(schema = @Schema(
                    implementation = MonthlyTrendResponse.class,
                    type = "array")))
    @ApiResponse(responseCode = "400", description = "Paramètre months invalide (1-24)",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(responseCode = "401", description = "JWT absent ou invalide",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(responseCode = "404", description = "Compte inconnu ou appartenant à un autre utilisateur",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    public List<MonthlyTrendResponse> monthlyTrend(
            @AuthenticationPrincipal Jwt jwt,
            @Parameter(description = "Nombre de mois (1-24)", example = "6")
            @RequestParam(defaultValue = "6") int months,
            @Parameter(description = "Filtre optionnel par compte appartenant à l'utilisateur")
            @RequestParam(required = false) UUID accountId
    ) {
        return dashboardService.getMonthlyTrend(months, JwtUserId.from(jwt), accountId);
    }
}
