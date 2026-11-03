package com.eparly.api.insights.api;

import com.eparly.api.auth.dto.ApiErrorResponse;
import com.eparly.api.auth.service.JwtUserId;
import com.eparly.api.insights.dto.InsightDtos.AnomaliesResponse;
import com.eparly.api.insights.dto.InsightDtos.OverviewResponse;
import com.eparly.api.insights.dto.InsightDtos.RecommendationStatusRequest;
import com.eparly.api.insights.dto.InsightDtos.RecommendationsResponse;
import com.eparly.api.insights.dto.InsightDtos.TrendsResponse;
import com.eparly.api.insights.service.InsightsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.YearMonth;
import java.util.UUID;

@RestController
@RequestMapping("/v1/insights")
@Tag(name = "Insights", description = "Analyses statistiques déterministes des transactions importées, sans modèle d'IA")
@SecurityRequirement(name = "bearerAuth")
@ApiResponse(responseCode = "400", description = "Paramètres invalides, ou mois futur",
        content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
@ApiResponse(responseCode = "401", description = "JWT absent ou invalide",
        content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
public class InsightsController {
    private final InsightsService service;

    public InsightsController(InsightsService service) {
        this.service = service;
    }

    @GetMapping("/overview")
    @Operation(summary = "Résumé d'un mois", description = "Dépenses, évolution, catégories et qualité des données. Vue potentiellement incomplète.")
    public OverviewResponse overview(@AuthenticationPrincipal Jwt jwt,
                                     @Parameter(description = "Mois analysé, mois courant par défaut", example = "2026-10")
                                     @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM") YearMonth month,
                                     @RequestParam(required = false) UUID accountId) {
        return service.overview(JwtUserId.from(jwt), month, accountId);
    }

    @GetMapping("/spending-trends")
    @Operation(summary = "Tendances par mois et par catégorie")
    public TrendsResponse trends(@AuthenticationPrincipal Jwt jwt,
                                 @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM") YearMonth month,
                                 @RequestParam(required = false) UUID accountId) {
        return service.trends(JwtUserId.from(jwt), month, accountId);
    }

    @GetMapping("/anomalies")
    @Operation(summary = "Dépenses inhabituelles", description = "Signalements à vérifier, jamais une affirmation de fraude.")
    public AnomaliesResponse anomalies(@AuthenticationPrincipal Jwt jwt,
                                       @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM") YearMonth month,
                                       @RequestParam(required = false) UUID accountId,
                                       @RequestParam(defaultValue = "0") int page,
                                       @RequestParam(defaultValue = "20") int size) {
        return service.anomalies(JwtUserId.from(jwt), month, accountId, page, size);
    }

    @GetMapping("/recommendations")
    @Operation(summary = "Recommandations classées", description = "Les recommandations ignorées ou traitées restent masquées pendant la durée configurée.")
    public RecommendationsResponse recommendations(@AuthenticationPrincipal Jwt jwt,
                                                   @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM") YearMonth month,
                                                   @RequestParam(defaultValue = "0") int page,
                                                   @RequestParam(defaultValue = "20") int size) {
        return service.recommendations(JwtUserId.from(jwt), jwt, month, page, size);
    }

    @PatchMapping("/recommendations/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Mettre à jour le statut d'une recommandation")
    @ApiResponse(responseCode = "204", description = "Statut enregistré")
    @ApiResponse(responseCode = "404", description = "Recommandation inconnue pour cet utilisateur",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    public void updateStatus(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
                             @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM") YearMonth month,
                             @Valid @RequestBody RecommendationStatusRequest request) {
        service.updateStatus(JwtUserId.from(jwt), id, request.status(), month);
    }
}
