package com.eparly.api.savings.action.api;

import com.eparly.api.savings.action.domain.ActionStatus;
import com.eparly.api.savings.action.dto.CreateSavingsActionRequest;
import com.eparly.api.savings.action.dto.SavingsActionListResponse;
import com.eparly.api.savings.action.dto.SavingsActionResponse;
import com.eparly.api.savings.action.dto.SavingsPlanSummaryResponse;
import com.eparly.api.savings.action.dto.UpdateSavingsActionStatusRequest;
import com.eparly.api.savings.action.service.SavingsActionService;
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
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/v1/savings/actions")
@Tag(name = "Plan d'économies", description = "Plan d'actions et suivi des économies (étape 11A)")
@SecurityRequirement(name = "bearerAuth")
public class SavingsActionController {

    private final SavingsActionService service;

    public SavingsActionController(SavingsActionService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Créer une action d'économie",
            description = "Ajoute une opportunité d'économie au plan personnel de l'utilisateur. "
                    + "Le backend réexécute l'estimation et ne fait jamais confiance au montant calculé par le frontend.")
    @ApiResponse(responseCode = "201", description = "Action créée", content = @Content(
            schema = @Schema(implementation = SavingsActionResponse.class)))
    @ApiResponse(responseCode = "400", description = "Requête invalide ou données insuffisantes")
    @ApiResponse(responseCode = "401", description = "JWT absent ou invalide")
    @ApiResponse(responseCode = "404", description = "Abonnement ou offre introuvable")
    @ApiResponse(responseCode = "409", description = "Action déjà existante pour cette opportunité")
    public SavingsActionResponse create(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody CreateSavingsActionRequest request
    ) {
        return service.create(jwt, request);
    }

    @GetMapping
    @Operation(summary = "Lister les actions d'économie",
            description = "Liste paginée des actions de l'utilisateur avec filtres optionnels par statut et abonnement.")
    @ApiResponse(responseCode = "200", description = "Liste paginée d'actions", content = @Content(
            schema = @Schema(implementation = SavingsActionListResponse.class)))
    @ApiResponse(responseCode = "401", description = "JWT absent ou invalide")
    public SavingsActionListResponse list(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) ActionStatus status,
            @RequestParam(required = false) UUID subscriptionId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return service.list(jwt, status, subscriptionId, page, size);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Consulter une action d'économie",
            description = "Retourne le détail d'une action avec son historique et son snapshot d'estimation.")
    @ApiResponse(responseCode = "200", description = "Action trouvée", content = @Content(
            schema = @Schema(implementation = SavingsActionResponse.class)))
    @ApiResponse(responseCode = "401", description = "JWT absent ou invalide")
    @ApiResponse(responseCode = "404", description = "Action introuvable ou n'appartenant pas à l'utilisateur")
    public SavingsActionResponse get(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID id
    ) {
        return service.get(jwt, id);
    }

    @PatchMapping("/{id}/status")
    @Operation(summary = "Changer le statut d'une action",
            description = "Transitions autorisées: PLANNED→COMMITTED/CANCELED, COMMITTED→DECLARED/CANCELED, DECLARED→CANCELED.")
    @ApiResponse(responseCode = "200", description = "Statut modifié", content = @Content(
            schema = @Schema(implementation = SavingsActionResponse.class)))
    @ApiResponse(responseCode = "400", description = "Transition non autorisée")
    @ApiResponse(responseCode = "401", description = "JWT absent ou invalide")
    @ApiResponse(responseCode = "404", description = "Action introuvable ou n'appartenant pas à l'utilisateur")
    public SavingsActionResponse updateStatus(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID id,
            @Valid @RequestBody UpdateSavingsActionStatusRequest request
    ) {
        return service.updateStatus(jwt, id, request);
    }

    @GetMapping("/summary")
    @Operation(summary = "Résumé du plan d'économies",
            description = "Indicateurs séparés : économies potentielles, engagées, déclarées, observées.")
    @ApiResponse(responseCode = "200", description = "Résumé du plan", content = @Content(
            schema = @Schema(implementation = SavingsPlanSummaryResponse.class)))
    @ApiResponse(responseCode = "401", description = "JWT absent ou invalide")
    public SavingsPlanSummaryResponse summary(
            @AuthenticationPrincipal Jwt jwt
    ) {
        return service.summary(jwt);
    }
}
