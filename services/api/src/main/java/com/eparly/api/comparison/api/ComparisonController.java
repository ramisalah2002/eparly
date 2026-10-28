package com.eparly.api.comparison.api;

import com.eparly.api.offer.domain.OfferSector;
import com.eparly.api.comparison.dto.ComparisonMetadataResponse;
import com.eparly.api.comparison.dto.ComparisonRequest;
import com.eparly.api.comparison.dto.ComparisonResponse;
import com.eparly.api.comparison.dto.ComparisonCriterionDetail;
import com.eparly.api.comparison.service.ComparisonService;
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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/v1/comparisons")
@Tag(name = "Comparaison d'offres", description = "Moteur de comparaison d'offres commerciales (étape 10B)")
@SecurityRequirement(name = "bearerAuth")
public class ComparisonController {

    private final ComparisonService service;

    public ComparisonController(ComparisonService service) {
        this.service = service;
    }

    @GetMapping("/criteria")
    @Operation(summary = "Critères de comparaison disponibles",
            description = "Retourne les critères de comparaison pour un secteur donné, avec leurs définitions.")
    @ApiResponse(responseCode = "200", description = "Liste des critères", content = @Content(
            schema = @Schema(implementation = ComparisonCriterionDetail.class)))
    @ApiResponse(responseCode = "401", description = "JWT absent ou invalide")
    @ApiResponse(responseCode = "404", description = "Secteur non supporté")
    public List<ComparisonCriterionDetail> criteria(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam OfferSector sector
    ) {
        // Le JWT est validé par Spring Security ; l'identité n'est pas nécessaire pour les critères publics
        return service.getCriteria(sector);
    }

    @PostMapping("/evaluate")
    @ResponseStatus(HttpStatus.OK)
    @Operation(summary = "Évaluer les offres alternatives",
            description = "Compare les offres vérifiées du catalogue avec les besoins de l'utilisateur pour un abonnement donné. "
                    + "Ne calcule pas d'économies (étape 10C).")
    @ApiResponse(responseCode = "200", description = "Résultats de comparaison", content = @Content(
            schema = @Schema(implementation = ComparisonResponse.class)))
    @ApiResponse(responseCode = "400", description = "Requête invalide")
    @ApiResponse(responseCode = "401", description = "JWT absent ou invalide")
    @ApiResponse(responseCode = "404", description = "Abonnement introuvable ou secteur non supporté")
    public ComparisonResponse evaluate(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody ComparisonRequest request
    ) {
        return service.evaluate(jwt, request);
    }

    @GetMapping("/metadata")
    @Operation(summary = "Métadonnées du moteur de comparaison",
            description = "Retourne les secteurs supportés, la version du moteur, la méthode de comparaison et les informations de transparence.")
    @ApiResponse(responseCode = "200", description = "Métadonnées", content = @Content(
            schema = @Schema(implementation = ComparisonMetadataResponse.class)))
    @ApiResponse(responseCode = "401", description = "JWT absent ou invalide")
    public ComparisonMetadataResponse metadata(
            @AuthenticationPrincipal Jwt jwt
    ) {
        return service.getMetadata();
    }
}
