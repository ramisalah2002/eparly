package com.eparly.api.catalogsync.api;

import com.eparly.api.catalogsync.orchestration.CatalogCandidateDraftService;
import com.eparly.api.catalogsync.orchestration.CatalogSyncAdminService;
import com.eparly.api.auth.service.JwtUserId;
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
@RequestMapping("/v1/admin/catalog-sync")
public class CatalogSyncAdminController {
    private final CatalogSyncAdminService service;

    private final CatalogCandidateDraftService draftService;

    public CatalogSyncAdminController(CatalogSyncAdminService service, CatalogCandidateDraftService draftService) {
        this.service = service;
        this.draftService = draftService;
    }

    @GetMapping("/connectors")
    public List<CatalogConnectorResponse> connectors() {
        return service.connectors();
    }

    @GetMapping("/runs/{id}/sources")
    public List<com.eparly.api.catalogsync.domain.CatalogSyncSourceReport> runSources(@PathVariable UUID id) {
        return service.runSources(id);
    }

    @PostMapping("/candidates/{id}/draft")
    @ResponseStatus(HttpStatus.CREATED)
    public com.eparly.api.offer.dto.CommercialOfferResponse createDraft(
            @PathVariable UUID id, @Valid @RequestBody CatalogCandidateDraftRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        return draftService.createDraft(id, request, JwtUserId.from(jwt));
    }

    @PostMapping("/sources")
    @ResponseStatus(HttpStatus.CREATED)
    public CatalogSyncSourceResponse createSource(@Valid @RequestBody CatalogSyncSourceCreateRequest request,
                                                   @AuthenticationPrincipal Jwt jwt) {
        return service.createSource(request, JwtUserId.from(jwt));
    }

    @GetMapping("/sources")
    public List<CatalogSyncSourceResponse> sources() {
        return service.sources();
    }

    @PatchMapping("/sources/{id}/permission")
    public CatalogSyncSourceResponse setPermission(@PathVariable UUID id,
                                                    @Valid @RequestBody CatalogSyncPermissionRequest request,
                                                    @AuthenticationPrincipal Jwt jwt) {
        return service.setPermission(id, request, JwtUserId.from(jwt));
    }

    @PostMapping("/runs")
    public com.eparly.api.catalogsync.domain.CatalogSyncReport runNow() {
        return service.runNow();
    }

    @GetMapping("/runs")
    public List<com.eparly.api.catalogsync.domain.CatalogSyncReport> recentRuns(
            @RequestParam(defaultValue = "20") int limit) {
        return service.recentRuns(limit);
    }

    @GetMapping("/candidates")
    public List<CatalogSyncCandidateResponse> candidates(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "50") int limit) {
        return service.candidates(status, limit);
    }

    @PatchMapping("/candidates/{id}/review")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void review(@PathVariable UUID id,
                       @Valid @RequestBody CatalogSyncCandidateReviewRequest request,
                       @AuthenticationPrincipal Jwt jwt) {
        service.reviewCandidate(id, request.status().name(), request.publishedOfferId(),
                JwtUserId.from(jwt));
    }
}
