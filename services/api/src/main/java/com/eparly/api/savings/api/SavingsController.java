package com.eparly.api.savings.api;

import com.eparly.api.offer.domain.OfferSector;
import com.eparly.api.savings.dto.SavingsEstimateRequest;
import com.eparly.api.savings.dto.SavingsEstimateResponse;
import com.eparly.api.savings.dto.SavingsMethodologyResponse;
import com.eparly.api.savings.service.SavingsEstimationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;

import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/savings")
@Tag(name = "Estimation d'économies", description = "Moteur d'estimation d'économies nettes potentielles (étape 10C)")
@SecurityRequirement(name = "bearerAuth")
public class SavingsController {

    private final SavingsEstimationService service;

    public SavingsController(SavingsEstimationService service) {
        this.service = service;
    }

    @PostMapping("/estimate")
    @ResponseStatus(HttpStatus.OK)
    @Operation(summary = "Estimer les économies nettes potentielles",
            description = "Calcule l'économie nette estimée si l'utilisateur remplace son abonnement par une offre alternative. "
                    + "Ne prétend pas qu'une économie a déjà été réalisée (étape 10C).")
    @ApiResponse(responseCode = "200", description = "Résultat d'estimation", content = @Content(
            schema = @Schema(implementation = SavingsEstimateResponse.class)))
    @ApiResponse(responseCode = "400", description = "Requête invalide ou données insuffisantes")
    @ApiResponse(responseCode = "401", description = "JWT absent ou invalide")
    @ApiResponse(responseCode = "404", description = "Abonnement ou offre introuvable")
    public SavingsEstimateResponse estimate(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody SavingsEstimateRequest request
    ) {
        return service.estimate(jwt, request);
    }

    @GetMapping("/methodology")
    @Operation(summary = "Méthodologie de calcul",
            description = "Documente les formules, horizons, hypothèses, arrondis et limites de l'estimation d'économies.")
    @ApiResponse(responseCode = "200", description = "Méthodologie", content = @Content(
            schema = @Schema(implementation = SavingsMethodologyResponse.class)))
    @ApiResponse(responseCode = "401", description = "JWT absent ou invalide")
    public SavingsMethodologyResponse methodology(
            @AuthenticationPrincipal Jwt jwt
    ) {
        return new SavingsMethodologyResponse(
                "estimatedNetSavings = currentTotalCost − alternativeTotalCost − switchingCost",
                List.of(12, 24),
                List.of("WEEKLY", "MONTHLY", "QUARTERLY", "YEARLY"),
                "EUR",
                "RoundingMode.HALF_UP, unité mineure (centimes)",
                "Prix promotionnel appliqué pendant la durée de la promotion, puis prix normal. Une promotion n'est jamais appliquée indéfiniment.",
                "Les frais inconnus ne sont jamais substitués par zéro. Leur absence rend le statut INSUFFICIENT_DATA.",
                "Montant déclaré (utilisateur) > montant observé (transactions) > inconnu. Les prix proviennent du catalogue vérifié (étape 10A).",
                "Vérification de moins de 30 jours (7 jours pour les promotions). Offres DRAFT, STALE, EXPIRED, ARCHIVED exclues.",
                "Estimation déterministe, non garantie. Ne constitue pas une promesse d'économie. Le catalogue n'est pas exhaustif.",
                "Aucune rémunération d'affiliation n'influence le montant des économies affichées."
        );
    }
}
