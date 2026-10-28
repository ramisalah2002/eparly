package com.eparly.api.subscription.api;

import com.eparly.api.auth.dto.ApiErrorResponse;
import com.eparly.api.auth.service.JwtUserId;
import com.eparly.api.subscription.domain.SubscriptionStatus;
import com.eparly.api.subscription.dto.CreateSubscriptionRequest;
import com.eparly.api.subscription.dto.SubscriptionResponse;
import com.eparly.api.subscription.dto.SubscriptionSummaryResponse;
import com.eparly.api.subscription.dto.UpdateSubscriptionRequest;
import com.eparly.api.subscription.dto.UpdateSubscriptionStatusRequest;
import com.eparly.api.subscription.service.SubscriptionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
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
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/v1/subscriptions")
@Tag(name = "Subscriptions", description = "Gestion des abonnements confirmes ou declares")
@SecurityRequirement(name = "bearerAuth")
public class SubscriptionController {

    private final SubscriptionService service;

    public SubscriptionController(SubscriptionService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "Lister ses abonnements", description = "Filtres optionnels par compte et statut declare.")
    public List<SubscriptionResponse> list(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) UUID accountId,
            @RequestParam(required = false) SubscriptionStatus status
    ) {
        return service.list(JwtUserId.from(jwt), accountId, status);
    }

    @GetMapping("/summary")
    @Operation(summary = "Estimation des couts recurrents", description = "Resume par devise des couts mensuels et annuels equivalents.")
    public SubscriptionSummaryResponse summary(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) UUID accountId
    ) {
        return service.summary(JwtUserId.from(jwt), accountId);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Detail d'un abonnement")
    public SubscriptionResponse get(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID id
    ) {
        return service.get(JwtUserId.from(jwt), id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Ajouter manuellement un abonnement ou transformer une recurrence detectee")
    public SubscriptionResponse create(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody CreateSubscriptionRequest request
    ) {
        return service.create(JwtUserId.from(jwt), request);
    }

    @PatchMapping("/{id}")
    @Operation(summary = "Modifier les informations declarees d'un abonnement")
    public SubscriptionResponse update(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID id,
            @Valid @RequestBody UpdateSubscriptionRequest request
    ) {
        return service.update(JwtUserId.from(jwt), id, request);
    }

    @PatchMapping("/{id}/status")
    @Operation(summary = "Declarer un changement d'etat (ACTIVE, PAUSED, CANCELED, UNKNOWN)")
    public SubscriptionResponse updateStatus(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID id,
            @Valid @RequestBody UpdateSubscriptionStatusRequest request
    ) {
        return service.updateStatus(JwtUserId.from(jwt), id, request);
    }
}
