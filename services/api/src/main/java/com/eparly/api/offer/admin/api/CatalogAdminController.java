package com.eparly.api.offer.admin.api;

import com.eparly.api.auth.service.JwtUserId;
import com.eparly.api.offer.admin.dto.AdminPageResponse;
import com.eparly.api.offer.admin.dto.AdminEvidenceResponse;
import com.eparly.api.offer.admin.dto.CatalogAuditResponse;
import com.eparly.api.offer.admin.dto.EvidenceCreateRequest;
import com.eparly.api.offer.admin.dto.EvidenceVerificationRequest;
import com.eparly.api.offer.admin.dto.OfferDraftRequest;
import com.eparly.api.offer.admin.dto.OfferPatchRequest;
import com.eparly.api.offer.admin.dto.OfferPriceHistoryResponse;
import com.eparly.api.offer.admin.dto.PricingUpsertRequest;
import com.eparly.api.offer.admin.dto.ProviderPatchRequest;
import com.eparly.api.offer.admin.service.CatalogAdminService;
import com.eparly.api.offer.domain.OfferSector;
import com.eparly.api.offer.domain.OfferStatus;
import com.eparly.api.offer.dto.CommercialOfferResponse;
import com.eparly.api.offer.dto.OfferPricingResponse;
import com.eparly.api.offer.dto.OfferProviderResponse;
import io.swagger.v3.oas.annotations.Operation;
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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/v1/admin/catalog")
@Tag(name = "Administration du catalogue", description = "Gestion sécurisée des fournisseurs et offres commerciales")
@SecurityRequirement(name = "bearerAuth")
public class CatalogAdminController {

    private final CatalogAdminService service;

    public CatalogAdminController(CatalogAdminService service) {
        this.service = service;
    }

    @PostMapping("/providers")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Créer un fournisseur")
    public OfferProviderResponse createProvider(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody ProviderCreateRequest request
    ) {
        return service.createProvider(JwtUserId.from(jwt), request.name(), request.sector(),
                request.officialWebsite(), request.country());
    }

    @GetMapping("/providers")
    @Operation(summary = "Lister les fournisseurs, actifs ou non")
    public AdminPageResponse<OfferProviderResponse> listProviders(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return service.listProviders(page, size);
    }

    @GetMapping("/providers/{providerId}")
    @Operation(summary = "Consulter un fournisseur")
    public OfferProviderResponse getProvider(@PathVariable UUID providerId) {
        return service.getProvider(providerId);
    }

    @PatchMapping("/providers/{providerId}")
    @Operation(summary = "Modifier ou désactiver un fournisseur")
    public OfferProviderResponse patchProvider(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID providerId,
            @Valid @RequestBody ProviderPatchRequest request
    ) {
        return service.patchProvider(JwtUserId.from(jwt), providerId, request);
    }

    @GetMapping("/providers/{providerId}/audit")
    @Operation(summary = "Consulter les actions administratives sur un fournisseur")
    public List<CatalogAuditResponse> providerAuditHistory(@PathVariable UUID providerId) {
        return service.providerAuditHistory(providerId);
    }

    @PostMapping("/offers")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Créer une offre en brouillon")
    public CommercialOfferResponse createDraft(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody OfferDraftRequest request
    ) {
        return service.createDraft(JwtUserId.from(jwt), request);
    }

    @GetMapping("/offers")
    @Operation(summary = "Lister les offres, y compris les brouillons")
    public AdminPageResponse<CommercialOfferResponse> listOffers(
            @RequestParam(required = false) OfferStatus status,
            @RequestParam(required = false) UUID providerId,
            @RequestParam(required = false) OfferSector sector,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return service.listOffers(status, providerId, sector, page, size);
    }

    @GetMapping("/offers/{offerId}")
    @Operation(summary = "Consulter une offre et ses données administratives")
    public CommercialOfferResponse getOffer(@PathVariable UUID offerId) {
        return service.getOffer(offerId);
    }

    @PatchMapping("/offers/{offerId}")
    @Operation(summary = "Modifier une offre non archivée")
    public CommercialOfferResponse patchOffer(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID offerId,
            @Valid @RequestBody OfferPatchRequest request
    ) {
        return service.patchOffer(JwtUserId.from(jwt), offerId, request);
    }

    @PutMapping("/offers/{offerId}/pricing")
    @Operation(summary = "Créer ou réviser les conditions tarifaires")
    public OfferPricingResponse upsertPricing(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID offerId,
            @Valid @RequestBody PricingUpsertRequest request
    ) {
        return service.upsertPricing(JwtUserId.from(jwt), offerId, request);
    }

    @PostMapping("/offers/{offerId}/evidences")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Ajouter une preuve officielle, initialement non vérifiée")
    public AdminEvidenceResponse addEvidence(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID offerId,
            @Valid @RequestBody EvidenceCreateRequest request
    ) {
        return service.addEvidence(JwtUserId.from(jwt), offerId, request);
    }

    @PatchMapping("/offers/{offerId}/evidences/{evidenceId}/verification")
    @Operation(summary = "Valider explicitement ou invalider une preuve")
    public AdminEvidenceResponse verifyEvidence(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID offerId,
            @PathVariable UUID evidenceId,
            @Valid @RequestBody EvidenceVerificationRequest request
    ) {
        return service.verifyEvidence(JwtUserId.from(jwt), offerId, evidenceId, request);
    }

    @PostMapping("/offers/{offerId}/publish")
    @Operation(summary = "Publier une offre après contrôles de disponibilité")
    public CommercialOfferResponse publish(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID offerId
    ) {
        return service.publish(JwtUserId.from(jwt), offerId);
    }

    @PostMapping("/offers/{offerId}/archive")
    @Operation(summary = "Archiver une offre")
    public CommercialOfferResponse archive(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID offerId
    ) {
        return service.archive(JwtUserId.from(jwt), offerId);
    }

    @GetMapping("/offers/{offerId}/price-history")
    @Operation(summary = "Consulter l'historique tarifaire complet")
    public List<OfferPriceHistoryResponse> priceHistory(@PathVariable UUID offerId) {
        return service.priceHistory(offerId);
    }

    @GetMapping("/offers/{offerId}/evidences")
    @Operation(summary = "Consulter les preuves et leurs validateurs")
    public List<AdminEvidenceResponse> evidenceHistory(@PathVariable UUID offerId) {
        return service.evidenceHistory(offerId);
    }

    @GetMapping("/offers/{offerId}/audit")
    @Operation(summary = "Consulter les actions administratives sur une offre")
    public List<CatalogAuditResponse> auditHistory(@PathVariable UUID offerId) {
        return service.auditHistory(offerId);
    }

    public record ProviderCreateRequest(
            @jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Size(max = 200) String name,
            @jakarta.validation.constraints.NotNull OfferSector sector,
            @jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Size(max = 500)
            String officialWebsite,
            @jakarta.validation.constraints.Pattern(regexp = "(?i)[a-z]{2}") String country
    ) {
    }
}
