package com.eparly.api.offer.api;

import com.eparly.api.auth.service.JwtUserId;
import com.eparly.api.offer.domain.OfferSector;
import com.eparly.api.offer.domain.OfferStatus;
import com.eparly.api.offer.dto.CommercialOfferResponse;
import com.eparly.api.offer.dto.OfferListResponse;
import com.eparly.api.offer.dto.OfferMetadataResponse;
import com.eparly.api.offer.dto.OfferProviderResponse;
import com.eparly.api.offer.service.OfferCatalogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/v1/offers")
@Tag(name = "Offre catalogue", description = "Catalogue d'offres commerciales verifiees (lecture seule)")
@SecurityRequirement(name = "bearerAuth")
public class OfferCatalogController {

    private final OfferCatalogService service;

    public OfferCatalogController(OfferCatalogService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "Lister les offres publiques",
            description = "Retourne les offres verifiees consultables, filtrees et paginees. "
                    + "Les offres DRAFT, STALE, EXPIREes et ARCHIVEes sont exclues par defaut.")
    @ApiResponse(responseCode = "200", description = "Liste paginee d'offres", content = @Content(
            schema = @Schema(implementation = OfferListResponse.class)))
    @ApiResponse(responseCode = "401", description = "JWT absent ou invalide")
    public OfferListResponse list(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) OfferSector sector,
            @RequestParam(required = false) UUID providerId,
            @RequestParam(required = false) String territory,
            @RequestParam(required = false) OfferStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        JwtUserId.from(jwt);
        return service.listPublicOffers(sector, providerId, territory, status, page, size);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Detail d'une offre publique",
            description = "Retourne le detail complet d'une offre verifiee : fournisseur, tarification, preuves, conditions.")
    @ApiResponse(responseCode = "200", description = "Offre trouvee", content = @Content(
            schema = @Schema(implementation = CommercialOfferResponse.class)))
    @ApiResponse(responseCode = "401", description = "JWT absent ou invalide")
    @ApiResponse(responseCode = "404", description = "Offre inconnue ou non disponible publiquement")
    public CommercialOfferResponse get(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID id
    ) {
        JwtUserId.from(jwt);
        return service.getPublicOffer(id);
    }

    @GetMapping("/metadata")
    @Operation(summary = "Metadonnees du catalogue",
            description = "Retourne les secteurs, periodicites, statuts publics et informations de couverture du catalogue.")
    @ApiResponse(responseCode = "200", description = "Metadonnees du catalogue", content = @Content(
            schema = @Schema(implementation = OfferMetadataResponse.class)))
    @ApiResponse(responseCode = "401", description = "JWT absent ou invalide")
    public OfferMetadataResponse metadata(
            @AuthenticationPrincipal Jwt jwt
    ) {
        JwtUserId.from(jwt);
        return service.getMetadata();
    }

    @GetMapping("/providers")
    @Operation(summary = "Lister les fournisseurs actifs",
            description = "Retourne les fournisseurs actifs du catalogue par secteur optionnel.")
    @ApiResponse(responseCode = "200", description = "Liste de fournisseurs", content = @Content(
            schema = @Schema(implementation = OfferProviderResponse.class)))
    @ApiResponse(responseCode = "401", description = "JWT absent ou invalide")
    public java.util.List<OfferProviderResponse> providers(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) OfferSector sector
    ) {
        JwtUserId.from(jwt);
        // Only ACTIVE providers are exposed
        if (sector != null) {
            return service.listProvidersBySector(sector).stream()
                    .map(this::toProviderResponse).toList();
        }
        return service.listAllProviders().stream()
                .map(this::toProviderResponse).toList();
    }

    private OfferProviderResponse toProviderResponse(com.eparly.api.offer.domain.OfferProvider p) {
        return new OfferProviderResponse(
                p.getId(), p.getName(), p.getSector(), p.getOfficialWebsite(),
                p.getCountry(), p.getStatus(), p.getCreatedAt(), p.getUpdatedAt()
        );
    }
}
