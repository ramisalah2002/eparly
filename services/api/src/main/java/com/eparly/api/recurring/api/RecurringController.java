package com.eparly.api.recurring.api;

import com.eparly.api.auth.dto.ApiErrorResponse;
import com.eparly.api.auth.exception.ApiException;
import com.eparly.api.auth.service.JwtUserId;
import com.eparly.api.recurring.domain.RecurringSeriesStatus;
import com.eparly.api.recurring.domain.RecurringSeriesType;
import com.eparly.api.recurring.dto.RecurringAnalyzeRequest;
import com.eparly.api.recurring.dto.RecurringAnalyzeResponse;
import com.eparly.api.recurring.dto.RecurringDecisionRequest;
import com.eparly.api.recurring.dto.RecurringSeriesDetailResponse;
import com.eparly.api.recurring.dto.RecurringSeriesResponse;
import com.eparly.api.recurring.service.RecurringDetectionService;
import com.eparly.api.recurring.service.RecurringSeriesService;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/v1/recurring")
@Tag(name = "Recurring", description = "Detection de paiements recurrents et abonnements potentiels")
@SecurityRequirement(name = "bearerAuth")
public class RecurringController {

    private final RecurringDetectionService detectionService;
    private final RecurringSeriesService seriesService;

    public RecurringController(RecurringDetectionService detectionService, RecurringSeriesService seriesService) {
        this.detectionService = detectionService;
        this.seriesService = seriesService;
    }

    @GetMapping("/candidates")
    @Operation(summary = "Suggestions non confirmees",
            description = "Retourne les series recurrentes au statut CANDIDATE pour l'utilisateur connecte.")
    public List<RecurringSeriesResponse> candidates(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) UUID accountId
    ) {
        return seriesService.candidates(JwtUserId.from(jwt), accountId);
    }

    @GetMapping
    @Operation(summary = "Series recurrentes",
            description = "Retourne les series recurrentes de l'utilisateur avec filtres optionnels.")
    public List<RecurringSeriesResponse> list(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) UUID accountId,
            @RequestParam(required = false) RecurringSeriesStatus status,
            @RequestParam(required = false) RecurringSeriesType type
    ) {
        return seriesService.list(JwtUserId.from(jwt), accountId, status, type);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Detail d'une serie recurrente")
    public RecurringSeriesDetailResponse detail(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID id
    ) {
        return seriesService.detail(JwtUserId.from(jwt), id);
    }

    @PatchMapping("/{id}/decision")
    @Operation(summary = "Confirmer ou rejeter une serie recurrente")
    public RecurringSeriesResponse decide(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID id,
            @Valid @RequestBody RecurringDecisionRequest request
    ) {
        return seriesService.decide(JwtUserId.from(jwt), id, request);
    }

    @PostMapping("/analyze")
    @Operation(summary = "Declencher une analyse des paiements recurrents")
    public RecurringAnalyzeResponse analyze(
            @AuthenticationPrincipal Jwt jwt,
            @RequestBody(required = false) RecurringAnalyzeRequest request
    ) {
        return detectionService.analyze(JwtUserId.from(jwt), request == null ? new RecurringAnalyzeRequest(null, false) : request);
    }
}
