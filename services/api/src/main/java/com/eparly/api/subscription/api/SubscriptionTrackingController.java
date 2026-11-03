package com.eparly.api.subscription.api;

import com.eparly.api.auth.dto.ApiErrorResponse;
import com.eparly.api.auth.service.JwtUserId;
import com.eparly.api.subscription.dto.SubscriptionResponse;
import com.eparly.api.subscription.dto.SubscriptionTrackingDtos.CancellationResponse;
import com.eparly.api.subscription.dto.SubscriptionTrackingDtos.ConfirmCancellationRequest;
import com.eparly.api.subscription.dto.SubscriptionTrackingDtos.DetectedSubscription;
import com.eparly.api.subscription.dto.SubscriptionTrackingDtos.LinkSeriesRequest;
import com.eparly.api.subscription.dto.SubscriptionTrackingDtos.Renewal;
import com.eparly.api.subscription.dto.SubscriptionTrackingDtos.StartCancellationRequest;
import com.eparly.api.subscription.dto.SubscriptionTrackingDtos.SubscriptionAnalysis;
import com.eparly.api.subscription.dto.SubscriptionTrackingDtos.SubscriptionEvent;
import com.eparly.api.subscription.dto.SubscriptionTrackingDtos.SubscriptionSavings;
import com.eparly.api.subscription.dto.SubscriptionTrackingDtos.SubscriptionTransactions;
import com.eparly.api.subscription.dto.SubscriptionTrackingDtos.SubscriptionsOverview;
import com.eparly.api.subscription.dto.SubscriptionTrackingDtos.SuspectCharge;
import com.eparly.api.subscription.dto.SubscriptionTrackingDtos.UpdateCancellationRequest;
import com.eparly.api.subscription.service.CancellationService;
import com.eparly.api.subscription.service.SubscriptionAnalysisService;
import com.eparly.api.subscription.service.SubscriptionService;
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

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/v1/subscriptions")
@Tag(name = "Subscriptions - suivi", description = "Coûts, renouvellements et suivi des résiliations. Eparly n'envoie aucune demande au fournisseur.")
@SecurityRequirement(name = "bearerAuth")
@ApiResponse(responseCode = "401", description = "JWT absent ou invalide", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
@ApiResponse(responseCode = "404", description = "Ressource inconnue pour cet utilisateur", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
public class SubscriptionTrackingController {
    private final SubscriptionService subscriptions;
    private final SubscriptionAnalysisService analysis;
    private final CancellationService cancellations;

    public SubscriptionTrackingController(SubscriptionService subscriptions, SubscriptionAnalysisService analysis, CancellationService cancellations) {
        this.subscriptions = subscriptions;
        this.analysis = analysis;
        this.cancellations = cancellations;
    }

    @GetMapping("/detected")
    @Operation(summary = "Abonnements détectés à confirmer", description = "Pour confirmer : POST /v1/subscriptions avec recurringSeriesId.")
    public List<DetectedSubscription> detected(@AuthenticationPrincipal Jwt jwt) {
        return analysis.detected(JwtUserId.from(jwt));
    }

    @GetMapping("/overview")
    @Operation(summary = "Coût global des abonnements actifs, par devise")
    public SubscriptionsOverview overview(@AuthenticationPrincipal Jwt jwt) {
        return analysis.overview(JwtUserId.from(jwt));
    }

    @GetMapping("/analysis")
    @Operation(summary = "Hausses de prix, redondances possibles et abonnements à examiner")
    public SubscriptionAnalysis analysis(@AuthenticationPrincipal Jwt jwt) {
        return analysis.analysis(JwtUserId.from(jwt));
    }

    @GetMapping("/renewals")
    @Operation(summary = "Prochains renouvellements et fins d'engagement", description = "dateStatus distingue une date confirmée d'une date estimée.")
    public List<Renewal> renewals(@AuthenticationPrincipal Jwt jwt, @RequestParam(defaultValue = "60") int days) {
        return analysis.renewals(JwtUserId.from(jwt), days);
    }

    @GetMapping("/post-cancellation-charges")
    @Operation(summary = "Prélèvements suspects après résiliation", description = "Enregistre les nouveaux événements, sans envoyer de notification.")
    public List<SuspectCharge> charges(@AuthenticationPrincipal Jwt jwt) {
        return cancellations.suspectCharges(JwtUserId.from(jwt));
    }

    @GetMapping("/{id}/transactions")
    @Operation(summary = "Transactions associées à un abonnement")
    public SubscriptionTransactions transactions(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return analysis.transactions(JwtUserId.from(jwt), id);
    }

    @GetMapping("/{id}/history")
    @Operation(summary = "Historique : corrections, archivage, résiliation et prélèvements suspects")
    public List<SubscriptionEvent> history(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return cancellations.history(JwtUserId.from(jwt), id);
    }

    @GetMapping("/{id}/savings")
    @Operation(summary = "Économies théorique, planifiée, après confirmation et constatée", description = "Une demande de résiliation n'est jamais comptée comme une économie réalisée.")
    public SubscriptionSavings savings(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return cancellations.savings(JwtUserId.from(jwt), id);
    }

    @PostMapping("/{id}/archive")
    @Operation(summary = "Archiver un abonnement")
    @ApiResponse(responseCode = "409", description = "Résiliation en cours", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    public SubscriptionResponse archive(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return subscriptions.archive(JwtUserId.from(jwt), id);
    }

    @PostMapping("/{id}/linked-series")
    @Operation(summary = "Rattacher une autre série, par exemple après un changement de compte")
    @ApiResponse(responseCode = "409", description = "Série déjà rattachée", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    public SubscriptionResponse link(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id, @Valid @RequestBody LinkSeriesRequest request) {
        return subscriptions.linkSeries(JwtUserId.from(jwt), id, request.seriesId());
    }

    @PostMapping("/{id}/cancellation")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Commencer une démarche de résiliation", description = "Enregistre votre intention. Rien n'est envoyé au fournisseur.")
    @ApiResponse(responseCode = "409", description = "Démarche déjà en cours", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    public CancellationResponse start(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id, @Valid @RequestBody(required = false) StartCancellationRequest request) {
        return cancellations.start(JwtUserId.from(jwt), id, request);
    }

    @GetMapping("/{id}/cancellation")
    @Operation(summary = "Dernière démarche et sa vérification")
    public CancellationResponse cancellation(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return cancellations.get(JwtUserId.from(jwt), id);
    }

    @PatchMapping("/{id}/cancellation")
    @Operation(summary = "Mettre à jour la démarche", description = "PLANNED → REQUESTED ou ABANDONED ; REQUESTED → FAILED ou ABANDONED ; CONFIRMED → ENDED ou FAILED.")
    @ApiResponse(responseCode = "409", description = "Transition interdite ou version périmée", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    public CancellationResponse update(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id, @Valid @RequestBody UpdateCancellationRequest request) {
        return cancellations.update(JwtUserId.from(jwt), id, request);
    }

    @PostMapping("/{id}/cancellation/confirmation")
    @Operation(summary = "Enregistrer une confirmation", description = "La source reste une déclaration de l'utilisateur : providerVerified est toujours faux.")
    @ApiResponse(responseCode = "409", description = "Transition interdite ou version périmée", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    public CancellationResponse confirm(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id, @Valid @RequestBody ConfirmCancellationRequest request) {
        return cancellations.confirm(JwtUserId.from(jwt), id, request);
    }
}
