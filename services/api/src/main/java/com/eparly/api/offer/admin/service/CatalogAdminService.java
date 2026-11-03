package com.eparly.api.offer.admin.service;

import com.eparly.api.auth.exception.ApiException;
import com.eparly.api.offer.admin.domain.CatalogActor;
import com.eparly.api.offer.admin.domain.CatalogAdminAudit;
import com.eparly.api.offer.admin.dto.AdminPageResponse;
import com.eparly.api.offer.admin.dto.AdminEvidenceResponse;
import com.eparly.api.offer.admin.dto.CatalogAuditResponse;
import com.eparly.api.offer.admin.dto.EvidenceCreateRequest;
import com.eparly.api.offer.admin.dto.EvidenceVerificationRequest;
import com.eparly.api.offer.admin.dto.OfferDraftRequest;
import com.eparly.api.offer.admin.dto.OfferPatchRequest;
import com.eparly.api.offer.admin.dto.OfferPriceHistoryResponse;
import com.eparly.api.offer.admin.dto.OfferReuseRightsRequest;
import com.eparly.api.offer.admin.dto.OfferReuseRightsResponse;
import com.eparly.api.offer.admin.dto.PricingUpsertRequest;
import com.eparly.api.offer.admin.dto.ProviderPatchRequest;
import com.eparly.api.offer.admin.repository.CatalogAdminAuditRepository;
import com.eparly.api.offer.domain.CommercialOffer;
import com.eparly.api.offer.domain.EvidenceStatus;
import com.eparly.api.offer.domain.OfferEvidence;
import com.eparly.api.offer.domain.OfferEvidenceScope;
import com.eparly.api.offer.domain.OfferPriceHistory;
import com.eparly.api.offer.domain.OfferPricing;
import com.eparly.api.offer.domain.OfferProvider;
import com.eparly.api.offer.domain.OfferReuseRights;
import com.eparly.api.offer.domain.OfferSector;
import com.eparly.api.offer.domain.OfferStatus;
import com.eparly.api.offer.domain.PromotionPriceType;
import com.eparly.api.offer.domain.ProviderStatus;
import com.eparly.api.offer.dto.CommercialOfferResponse;
import com.eparly.api.offer.dto.OfferPricingResponse;
import com.eparly.api.offer.dto.OfferProviderResponse;
import com.eparly.api.offer.repository.CommercialOfferRepository;
import com.eparly.api.offer.repository.OfferEvidenceRepository;
import com.eparly.api.offer.repository.OfferPriceHistoryRepository;
import com.eparly.api.offer.repository.OfferPricingRepository;
import com.eparly.api.offer.repository.OfferProviderRepository;
import com.eparly.api.offer.service.OfferAvailabilityService;
import com.eparly.api.offer.service.OfferCatalogService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class CatalogAdminService {

    private static final int MAX_PAGE_SIZE = 100;

    private final OfferCatalogService catalogService;
    private final OfferAvailabilityService availabilityService;
    private final OfferProviderRepository providerRepository;
    private final CommercialOfferRepository offerRepository;
    private final OfferPricingRepository pricingRepository;
    private final OfferEvidenceRepository evidenceRepository;
    private final OfferPriceHistoryRepository priceHistoryRepository;
    private final CatalogAdminAuditRepository auditRepository;
    private final ObjectMapper objectMapper;

    public CatalogAdminService(
            OfferCatalogService catalogService,
            OfferAvailabilityService availabilityService,
            OfferProviderRepository providerRepository,
            CommercialOfferRepository offerRepository,
            OfferPricingRepository pricingRepository,
            OfferEvidenceRepository evidenceRepository,
            OfferPriceHistoryRepository priceHistoryRepository,
            CatalogAdminAuditRepository auditRepository,
            ObjectMapper objectMapper
    ) {
        this.catalogService = catalogService;
        this.availabilityService = availabilityService;
        this.providerRepository = providerRepository;
        this.offerRepository = offerRepository;
        this.pricingRepository = pricingRepository;
        this.evidenceRepository = evidenceRepository;
        this.priceHistoryRepository = priceHistoryRepository;
        this.auditRepository = auditRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public OfferProviderResponse createProvider(
            UUID actorId, String name, OfferSector sector, String officialWebsite, String country
    ) {
        return createProvider(CatalogActor.admin(actorId), name, sector, officialWebsite, country);
    }

    @Transactional
    public OfferProviderResponse createProvider(
            CatalogActor actorId, String name, OfferSector sector, String officialWebsite, String country
    ) {
        if (sector == null || providerRepository.existsByNameIgnoreCaseAndSector(name.trim(), sector)) {
            throw new ApiException(HttpStatus.CONFLICT, "Provider already exists or sector is invalid");
        }
        OfferProviderResponse created = catalogService.createProvider(
                name, sector, officialWebsite, country == null ? null : country.trim().toUpperCase());
        audit(actorId, "PROVIDER_CREATED", "PROVIDER", created.id(), details(
                "name", created.name(), "sector", created.sector()));
        return created;
    }

    public AdminPageResponse<OfferProviderResponse> listProviders(int page, int size) {
        Page<OfferProvider> result = providerRepository.findAll(pageRequest(page, size, "name"));
        return new AdminPageResponse<>(result.getContent().stream().map(this::providerResponse).toList(),
                result.getNumber(), result.getSize(), result.getTotalElements(), result.getTotalPages());
    }

    public OfferProviderResponse getProvider(UUID providerId) {
        OfferProvider provider = providerRepository.findById(providerId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Provider not found"));
        return providerResponse(provider);
    }

    @Transactional
    public OfferProviderResponse patchProvider(UUID actorId, UUID providerId, ProviderPatchRequest request) {
        OfferProvider provider = providerRepository.findById(providerId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Provider not found"));
        requireVersion(request.expectedVersion(), provider.getVersion(), "Provider has changed; reload it");

        OfferSector newSector = request.sector() == null ? provider.getSector() : request.sector();
        if (!newSector.equals(provider.getSector()) && offerRepository.existsByProviderId(providerId)) {
            throw new ApiException(HttpStatus.CONFLICT, "Provider sector cannot change while offers exist");
        }
        String newName = request.name() == null ? provider.getName() : request.name().trim();
        if (newName.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Provider name cannot be blank");
        }
        if (providerRepository.existsByNameIgnoreCaseAndSectorAndIdNot(newName, newSector, providerId)) {
            throw new ApiException(HttpStatus.CONFLICT, "Provider already exists for this sector");
        }
        if (request.officialWebsite() != null && !isValidHttpsUrl(request.officialWebsite())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Official website must be a valid HTTPS URL");
        }
        String newCountry = request.country() == null
                ? provider.getCountry() : request.country().trim().toUpperCase();
        String newWebsite = request.officialWebsite() == null
                ? provider.getOfficialWebsite() : request.officialWebsite().trim();
        if (newCountry.equals(provider.getCountry()) && newName.equals(provider.getName())
                && newWebsite.equals(provider.getOfficialWebsite()) && newSector == provider.getSector()
                && (request.status() == null || request.status() == provider.getStatus())) {
            return providerResponse(provider);
        }

        Map<String, Object> before = providerDetails(provider);
        provider.setName(newName);
        provider.setSector(newSector);
        provider.setOfficialWebsite(newWebsite);
        provider.setCountry(newCountry);
        if (request.status() != null) provider.setStatus(request.status());
        OfferProvider saved = providerRepository.saveAndFlush(provider);

        if (!before.equals(providerDetails(saved))) {
            for (CommercialOffer offer : offerRepository.findByProviderId(providerId)) {
                if (offer.getPublicationStatus() == OfferStatus.VERIFIED) {
                    offer.setPublicationStatus(OfferStatus.STALE);
                    offerRepository.save(offer);
                }
            }
            audit(actorId, "PROVIDER_UPDATED", "PROVIDER", providerId,
                    details("before", before, "after", providerDetails(saved)));
        }
        return providerResponse(saved);
    }

    @Transactional
    public CommercialOfferResponse createDraft(UUID actorId, OfferDraftRequest request) {
        return createDraft(CatalogActor.admin(actorId), request);
    }

    @Transactional
    public CommercialOfferResponse createDraft(CatalogActor actorId, OfferDraftRequest request) {
        validateOfferDates(request.validFrom(), request.validUntil());
        validateCharacteristics(request.characteristics(), request.sector());
        CommercialOfferResponse created = catalogService.createDraftOffer(
                request.providerId(), request.commercialName(), request.sector(), request.characteristics(),
                request.eligibilityConditions(), request.commitment(), request.territory(),
                request.validFrom(), request.validUntil());
        audit(actorId, "OFFER_DRAFT_CREATED", "OFFER", created.id(),
                details("providerId", request.providerId(), "sector", request.sector()));
        return created;
    }

    public AdminPageResponse<CommercialOfferResponse> listOffers(
            OfferStatus status, UUID providerId, OfferSector sector, int page, int size
    ) {
        Specification<CommercialOffer> spec = (root, query, cb) -> cb.conjunction();
        if (status != null) spec = spec.and((root, query, cb) -> cb.equal(root.get("publicationStatus"), status));
        if (providerId != null) spec = spec.and((root, query, cb) -> cb.equal(root.get("provider").get("id"), providerId));
        if (sector != null) spec = spec.and((root, query, cb) -> cb.equal(root.get("sector"), sector));
        Page<CommercialOffer> result = offerRepository.findAll(spec, pageRequest(page, size, "createdAt"));
        List<CommercialOfferResponse> items = result.getContent().stream()
                .map(offer -> catalogService.getAdminOffer(offer.getId())).toList();
        return new AdminPageResponse<>(items, result.getNumber(), result.getSize(),
                result.getTotalElements(), result.getTotalPages());
    }

    public CommercialOfferResponse getOffer(UUID offerId) {
        return catalogService.getAdminOffer(offerId);
    }

    @Transactional
    public CommercialOfferResponse patchOffer(UUID actorId, UUID offerId, OfferPatchRequest request) {
        return patchOffer(CatalogActor.admin(actorId), offerId, request);
    }

    @Transactional
    public CommercialOfferResponse patchOffer(CatalogActor actorId, UUID offerId, OfferPatchRequest request) {
        CommercialOffer offer = requireOffer(offerId);
        requireVersion(request.expectedVersion(), offer.getVersion(), "Offer has changed; reload it");
        if (offer.getPublicationStatus() == OfferStatus.ARCHIVED) {
            throw new ApiException(HttpStatus.CONFLICT, "Archived offers cannot be edited");
        }
        if (request.commercialName() == null && request.characteristics() == null
                && request.eligibilityConditions() == null && request.commitment() == null
                && request.territory() == null && request.validFrom() == null && request.validUntil() == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "At least one offer field must be provided");
        }
        String name = request.commercialName() == null ? offer.getCommercialName() : request.commercialName().trim();
        String territory = request.territory() == null ? offer.getTerritory() : request.territory().trim().toUpperCase();
        var from = request.validFrom() == null ? offer.getValidFrom() : request.validFrom();
        var until = request.validUntil() == null ? offer.getValidUntil() : request.validUntil();
        validateOfferDates(from, until);
        validateCharacteristics(request.characteristics(), offer.getSector());

        Map<String, Object> before = offerDetails(offer);
        Map<String, Object> contentBefore = offerContentDetails(offer);
        offer.setCommercialName(name);
        offer.setTerritory(territory);
        if (request.characteristics() != null) offer.setCharacteristics(request.characteristics());
        if (request.eligibilityConditions() != null) offer.setEligibilityConditions(request.eligibilityConditions());
        if (request.commitment() != null) offer.setCommitment(request.commitment());
        offer.setValidFrom(from);
        offer.setValidUntil(until);
        if (contentBefore.equals(offerContentDetails(offer))) {
            return catalogService.getAdminOffer(offerId);
        }
        if (offer.getPublicationStatus() != OfferStatus.DRAFT) {
            offer.setPublicationStatus(OfferStatus.DRAFT);
        }
        CommercialOffer saved = offerRepository.saveAndFlush(offer);
        List<UUID> invalidatedEvidenceIds = new java.util.ArrayList<>();
        for (OfferEvidence evidence : evidenceRepository.findAllByOfferIdOrderByConsultedAtDescCreatedAtDescIdDesc(
                offerId)) {
            if (evidence.getVerificationStatus() == EvidenceStatus.VERIFIED) {
                evidence.setVerificationStatus(EvidenceStatus.INVALID);
                evidenceRepository.save(evidence);
                invalidatedEvidenceIds.add(evidence.getId());
            }
        }
        audit(actorId, "OFFER_UPDATED", "OFFER", offerId,
                details("before", before, "after", offerDetails(saved),
                        "invalidatedEvidenceIds", invalidatedEvidenceIds));
        return catalogService.getAdminOffer(offerId);
    }

    @Transactional
    public OfferPricingResponse upsertPricing(UUID actorId, UUID offerId, PricingUpsertRequest request) {
        return upsertPricing(CatalogActor.admin(actorId), offerId, request);
    }

    @Transactional
    public OfferPricingResponse upsertPricing(CatalogActor actorId, UUID offerId, PricingUpsertRequest request) {
        CommercialOffer offer = requireOffer(offerId);
        if (offer.getPublicationStatus() == OfferStatus.ARCHIVED) {
            throw new ApiException(HttpStatus.CONFLICT, "Archived offers cannot be repriced");
        }
        validatePricing(request);
        OfferPricing pricing = pricingRepository.findByOfferId(offerId).orElse(null);
        Map<String, Object> oldSnapshot = pricing == null ? null : pricingSnapshot(pricing);
        if (pricing == null) {
            if (request.expectedVersion() != null) {
                throw new ApiException(HttpStatus.CONFLICT, "Pricing does not exist; reload the offer");
            }
            pricing = new OfferPricing();
            pricing.setOffer(offer);
        } else {
            if (request.expectedVersion() == null) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "expectedVersion is required when revising pricing");
            }
            requireVersion(request.expectedVersion(), pricing.getVersion(), "Pricing has changed; reload it");
        }
        applyPricing(pricing, request);
        Map<String, Object> newSnapshot = pricingSnapshot(pricing);
        if (oldSnapshot != null && oldSnapshot.equals(newSnapshot)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Pricing request contains no changes");
        }

        OfferPricing savedPricing = pricingRepository.saveAndFlush(pricing);
        OfferPriceHistory history = new OfferPriceHistory();
        history.setOffer(offer);
        history.setOldAmountMinor(oldSnapshot == null ? null : pricingAmount(oldSnapshot));
        history.setNewAmountMinor(savedPricing.getAmountMinor());
        history.setOldPricingSnapshot(oldSnapshot);
        history.setNewPricingSnapshot(pricingSnapshot(savedPricing));
        history.setReason(request.reason());
        priceHistoryRepository.save(history);
        if (offer.getPublicationStatus() != OfferStatus.DRAFT) {
            offer.setPublicationStatus(OfferStatus.DRAFT);
            offerRepository.saveAndFlush(offer);
        }
        audit(actorId, oldSnapshot == null ? "PRICING_CREATED" : "PRICING_REVISED", "OFFER", offerId,
                details("oldPricing", oldSnapshot, "newPricing", pricingSnapshot(savedPricing),
                        "pricingVersion", savedPricing.getVersion()));
        return catalogService.getAdminOffer(offerId).pricing();
    }

    @Transactional
    public AdminEvidenceResponse addEvidence(UUID actorId, UUID offerId, EvidenceCreateRequest request) {
        return addEvidence(CatalogActor.admin(actorId), offerId, request);
    }

    @Transactional
    public AdminEvidenceResponse addEvidence(CatalogActor actorId, UUID offerId, EvidenceCreateRequest request) {
        CommercialOffer offer = requireOffer(offerId);
        validateSourceDomain(offer.getProvider(), request.officialUrl());
        Instant now = availabilityService.currentInstant();
        if (request.consultedAt().isAfter(now)
                || (request.expiresAt() != null && !request.expiresAt().isAfter(now))) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Evidence dates must not be in the future or already expired");
        }
        if (evidenceRepository.existsByOfferIdAndOfficialUrlAndEvidenceScopeAndConsultedAt(
                offerId, request.officialUrl().trim(), request.evidenceScope(), request.consultedAt())) {
            throw new ApiException(HttpStatus.CONFLICT, "This evidence was already added");
        }
        OfferEvidence evidence = new OfferEvidence();
        evidence.setOffer(offer);
        evidence.setOfficialUrl(request.officialUrl().trim());
        evidence.setEvidenceScope(request.evidenceScope());
        evidence.setNature(request.nature().trim());
        evidence.setProvenance(request.provenance().trim());
        evidence.setConsultedAt(request.consultedAt());
        evidence.setExpiresAt(request.expiresAt());
        evidence.setVerificationStatus(EvidenceStatus.PENDING);
        evidence.setVerifiedAt(null);
        evidence.setVerifiedByUserId(null);
        OfferEvidence saved = evidenceRepository.saveAndFlush(evidence);
        if (request.evidenceScope() == OfferEvidenceScope.OFFER
                && offer.getPublicationStatus() == OfferStatus.VERIFIED) {
            offer.setPublicationStatus(OfferStatus.DRAFT);
            offerRepository.saveAndFlush(offer);
        }
        audit(actorId, "EVIDENCE_ADDED", "EVIDENCE", saved.getId(),
                details("offerId", offerId, "scope", saved.getEvidenceScope(), "status", saved.getVerificationStatus()));
        return evidenceResponse(saved);
    }

    @Transactional
    public AdminEvidenceResponse verifyEvidence(
            UUID actorId, UUID offerId, UUID evidenceId, EvidenceVerificationRequest request
    ) {
        CommercialOffer offer = requireOffer(offerId);
        OfferEvidence evidence = evidenceRepository.findById(evidenceId)
                .filter(item -> item.getOffer().getId().equals(offerId))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Evidence not found"));
        requireVersion(request.expectedVersion(), evidence.getVersion(), "Evidence has changed; reload it");
        if (request.status() != EvidenceStatus.VERIFIED && request.status() != EvidenceStatus.INVALID) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Evidence status must be VERIFIED or INVALID");
        }
        Map<String, Object> before = details("status", evidence.getVerificationStatus(),
                "verifiedAt", evidence.getVerifiedAt(), "verifiedByUserId", evidence.getVerifiedByUserId());
        evidence.setVerificationStatus(request.status());
        evidence.setVerifiedSummary(request.summary().trim());
        if (request.status() == EvidenceStatus.INVALID
                && evidence.getEvidenceScope() == OfferEvidenceScope.OFFER
                && offer.getPublicationStatus() == OfferStatus.VERIFIED) {
            offer.setPublicationStatus(OfferStatus.DRAFT);
            offerRepository.saveAndFlush(offer);
        }
        if (request.status() == EvidenceStatus.VERIFIED) {
            Instant now = availabilityService.currentInstant();
            validateSourceDomain(offer.getProvider(), evidence.getOfficialUrl());
            if (evidence.getExpiresAt() != null && !evidence.getExpiresAt().isAfter(now)) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "Expired evidence cannot be verified");
            }
            evidence.setConsultedAt(now);
            evidence.setVerifiedAt(now);
            evidence.setVerifiedByUserId(actorId);
            OfferPricing pricing = pricingRepository.findByOfferId(offerId).orElse(null);
            evidence.setVerifiedPricingVersion(pricing == null ? null : pricing.getVersion());
            evidence.setVerifiedProviderVersion(offer.getProvider().getVersion());
        }
        OfferEvidence saved = evidenceRepository.saveAndFlush(evidence);
        audit(CatalogActor.manualVerification(actorId), request.status() == EvidenceStatus.VERIFIED ? "EVIDENCE_VERIFIED" : "EVIDENCE_INVALIDATED",
                "EVIDENCE", evidenceId, details("offerId", offerId, "before", before,
                        "after", details("status", saved.getVerificationStatus(),
                                "verifiedAt", saved.getVerifiedAt(), "verifiedByUserId", saved.getVerifiedByUserId())));
        return evidenceResponse(saved);
    }

    public OfferReuseRightsResponse getReuseRights(UUID offerId) {
        return reuseRightsResponse(requireOffer(offerId));
    }

    /** Decision humaine : seule une personne peut etablir les droits de reutilisation d'une offre. */
    @Transactional
    public OfferReuseRightsResponse updateReuseRights(UUID actorId, UUID offerId, OfferReuseRightsRequest request) {
        CommercialOffer offer = requireOffer(offerId);
        requireVersion(request.expectedVersion(), offer.getVersion(), "Offer has changed; reload it");
        Map<String, Object> before = details("status", offer.getReuseRightsStatus(), "basis", offer.getReuseRightsBasis());
        offer.setReuseRightsStatus(request.status());
        offer.setReuseRightsBasis(request.basis().trim());
        if (request.status() != OfferReuseRights.ESTABLISHED
                && offer.getPublicationStatus() == OfferStatus.VERIFIED) {
            offer.setPublicationStatus(OfferStatus.DRAFT);
        }
        CommercialOffer saved = offerRepository.saveAndFlush(offer);
        audit(CatalogActor.manualVerification(actorId), "OFFER_REUSE_RIGHTS_UPDATED", "OFFER", offerId,
                details("before", before, "after",
                        details("status", saved.getReuseRightsStatus(), "basis", saved.getReuseRightsBasis())));
        return reuseRightsResponse(saved);
    }

    private OfferReuseRightsResponse reuseRightsResponse(CommercialOffer offer) {
        return new OfferReuseRightsResponse(offer.getId(), offer.getReuseRightsStatus(),
                offer.getReuseRightsBasis(), offer.getVersion());
    }

    @Transactional
    public CommercialOfferResponse publish(UUID actorId, UUID offerId) {
        CommercialOffer offer = requireOffer(offerId);
        if (offer.getReferenceKind() != null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Reference tariffs cannot be published as offers");
        }
        OfferPricing pricing = pricingRepository.findByOfferId(offerId)
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "Publication requires pricing"));
        if (!availabilityService.isReliablePricing(pricing)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Publication requires reliable recurring pricing");
        }
        if (offer.getValidFrom() != null && offer.getValidFrom().isAfter(availabilityService.currentDate())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Future offers remain drafts until their start date");
        }
        OfferEvidence evidence = availabilityService.currentEvidence(offer)
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST,
                        "Publication requires fresh, verified offer evidence"));
        if (evidence.getEvidenceScope() != null && evidence.getEvidenceScope() != OfferEvidenceScope.OFFER) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Publication requires evidence scoped to the offer");
        }
        if (!Objects.equals(evidence.getVerifiedPricingVersion(), pricing.getVersion())
                || !Objects.equals(evidence.getVerifiedProviderVersion(), offer.getProvider().getVersion())
                || evidence.getVerifiedByUserId() == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST,
                    "Offer, provider, or pricing changed after the evidence was validated");
        }
        if (offer.getPublicationStatus() == OfferStatus.ARCHIVED) {
            throw new ApiException(HttpStatus.CONFLICT, "Archived offers cannot be published");
        }
        if (offer.getPublicationStatus() == OfferStatus.VERIFIED
                && availabilityService.evaluate(offer, offer.getTerritory()).available()) {
            return catalogService.getAdminOffer(offerId);
        }
        CommercialOfferResponse published = catalogService.validateAndPublish(offerId);
        if (!availabilityService.evaluate(requireOffer(offerId), offer.getTerritory()).available()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Offer does not satisfy current availability policy");
        }
        audit(actorId, "OFFER_PUBLISHED", "OFFER", offerId,
                details("offerVersion", published.version(), "pricingVersion", pricing.getVersion(),
                        "evidenceId", evidence.getId()));
        return published;
    }

    @Transactional
    public CommercialOfferResponse archive(UUID actorId, UUID offerId) {
        CommercialOffer offer = requireOffer(offerId);
        if (offer.getPublicationStatus() != OfferStatus.ARCHIVED) {
            catalogService.archive(offerId);
            offer = requireOffer(offerId);
            audit(actorId, "OFFER_ARCHIVED", "OFFER", offerId, details("offerVersion", offer.getVersion()));
        }
        return catalogService.getAdminOffer(offerId);
    }

    public List<OfferPriceHistoryResponse> priceHistory(UUID offerId) {
        requireOffer(offerId);
        return priceHistoryRepository.findAllByOfferIdOrderByChangedAtDesc(offerId).stream()
                .map(this::historyResponse).toList();
    }

    public List<AdminEvidenceResponse> evidenceHistory(UUID offerId) {
        requireOffer(offerId);
        return evidenceRepository.findAllByOfferIdOrderByConsultedAtDescCreatedAtDescIdDesc(offerId).stream()
                .map(this::evidenceResponse).toList();
    }

    public List<CatalogAuditResponse> auditHistory(UUID offerId) {
        requireOffer(offerId);
        return auditHistory("OFFER", offerId);
    }

    public List<CatalogAuditResponse> providerAuditHistory(UUID providerId) {
        if (!providerRepository.existsById(providerId)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Provider not found");
        }
        return auditHistory("PROVIDER", providerId);
    }

    private List<CatalogAuditResponse> auditHistory(String resourceType, UUID resourceId) {
        return auditRepository.findAllByResourceTypeAndResourceIdOrderByOccurredAtDesc(resourceType, resourceId)
                .stream().map(this::auditResponse).toList();
    }

    private void validatePricing(PricingUpsertRequest request) {
        if (request.billingPeriod() == null || request.currency() == null
                || !request.currency().matches("[A-Z]{3}")) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Currency and billing period are required and valid");
        }
        PromotionPriceType type = request.promotionPriceType();
        if (type == null) {
            if (request.promotionPercent() != null || request.promotionDurationMonths() != null
                    || request.priceAfterPromotionMinor() != null || request.promotionValidFrom() != null
                    || request.promotionValidUntil() != null) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "Promotion fields require an explicit price type");
            }
            return;
        }
        if (request.amountMinor() == null || request.promotionDurationMonths() == null
                || request.promotionDurationMonths() <= 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "A promotion requires a standard price and duration");
        }
        if (request.promotionValidFrom() != null && request.promotionValidUntil() != null
                && request.promotionValidUntil().isBefore(request.promotionValidFrom())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Promotion dates are inconsistent");
        }
        boolean valid = switch (type) {
            case PERCENTAGE -> request.promotionPercent() != null
                    && request.promotionPercent() > 0 && request.promotionPercent() <= 100
                    && request.priceAfterPromotionMinor() == null;
            case FIXED_PRICE -> request.promotionPercent() == null
                    && request.priceAfterPromotionMinor() != null
                    && request.priceAfterPromotionMinor() <= request.amountMinor();
        };
        if (!valid) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Promotion fields are inconsistent with the selected type");
        }
    }

    private void applyPricing(OfferPricing pricing, PricingUpsertRequest request) {
        pricing.setAmountMinor(request.amountMinor());
        pricing.setCurrency(request.currency());
        pricing.setBillingPeriod(request.billingPeriod());
        pricing.setActivationFeeMinor(request.activationFeeMinor());
        pricing.setOtherFees(request.otherFees());
        pricing.setPromotionPriceType(request.promotionPriceType());
        pricing.setPromotionPercent(request.promotionPercent());
        pricing.setPromotionDurationMonths(request.promotionDurationMonths() == null
                ? null : Math.toIntExact(request.promotionDurationMonths()));
        pricing.setPriceAfterPromotionMinor(request.priceAfterPromotionMinor());
        pricing.setPromotionValidFrom(request.promotionValidFrom());
        pricing.setPromotionValidUntil(request.promotionValidUntil());
        pricing.setDiscountConditions(request.discountConditions());
        pricing.setTaxesIncluded(request.taxesIncluded());
        pricing.setDataUnknown(request.dataUnknown());
    }

    private void validateOfferDates(java.time.LocalDate validFrom, java.time.LocalDate validUntil) {
        if (validFrom != null && validUntil != null && validUntil.isBefore(validFrom)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Offer validity dates are inconsistent");
        }
    }

    private void validateCharacteristics(String characteristics, OfferSector sector) {
        if (characteristics == null || characteristics.isBlank()) return;
        try {
            JsonNode parsed = objectMapper.readTree(characteristics);
            if (parsed == null || !parsed.isObject()) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "Characteristics must be a JSON object");
            }
            validateKnownCharacteristics(parsed, sector);
        } catch (JacksonException exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Characteristics must be valid JSON");
        }
    }

    private void validateKnownCharacteristics(JsonNode characteristics, OfferSector sector) {
        if (sector == null) return;
        if (sector == OfferSector.STREAMING) {
            rejectPresent(characteristics, "dataGb", "network5G", "roaming", "roamingDataGb");
            validateIntegerCharacteristic(characteristics, "screens", 1);
            validateBooleanCharacteristic(characteristics, "ads");
            validateBooleanCharacteristic(characteristics, "offlineDownload");
            JsonNode quality = characteristics.get("videoQuality");
            if (quality != null && !List.of("SD", "HD", "4K").contains(quality.asText())) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "videoQuality must be SD, HD, or 4K");
            }
        } else if (sector == OfferSector.MOBILE_PLAN) {
            rejectPresent(characteristics, "screens", "videoQuality", "ads", "offlineDownload");
            validateIntegerCharacteristic(characteristics, "dataGb", 0);
            validateIntegerCharacteristic(characteristics, "roamingDataGb", 0);
            validateBooleanCharacteristic(characteristics, "network5G");
            validateBooleanCharacteristic(characteristics, "roaming");
        } else {
            rejectPresent(characteristics, "dataGb", "network5G", "roaming", "roamingDataGb",
                    "screens", "videoQuality", "ads", "offlineDownload");
        }
    }

    private void validateIntegerCharacteristic(JsonNode object, String field, int minimum) {
        JsonNode value = object.get(field);
        if (value != null && (!value.isIntegralNumber() || !value.canConvertToInt()
                || value.intValue() < minimum)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, field + " must be an integer greater than or equal to "
                    + minimum);
        }
    }

    private void validateBooleanCharacteristic(JsonNode object, String field) {
        JsonNode value = object.get(field);
        if (value != null && !value.isBoolean()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, field + " must be a boolean");
        }
    }

    private void rejectPresent(JsonNode object, String... incompatibleKeys) {
        for (String key : incompatibleKeys) {
            if (object.has(key)) {
                throw new ApiException(HttpStatus.BAD_REQUEST,
                        "Characteristic " + key + " is incompatible with the offer sector");
            }
        }
    }

    private void validateSourceDomain(OfferProvider provider, String sourceUrl) {
        if (!isValidHttpsUrl(sourceUrl) || !isSameOrSubdomain(sourceUrl, provider.getOfficialWebsite())) {
            throw new ApiException(HttpStatus.BAD_REQUEST,
                    "Evidence URL must use the provider's official HTTPS domain or a subdomain");
        }
    }

    private static boolean isSameOrSubdomain(String sourceUrl, String officialWebsite) {
        try {
            String sourceHost = normalizeHost(new URI(sourceUrl).getHost());
            String officialHost = normalizeHost(new URI(officialWebsite).getHost());
            return sourceHost.equals(officialHost) || sourceHost.endsWith("." + officialHost);
        } catch (URISyntaxException exception) {
            return false;
        }
    }

    private static String normalizeHost(String host) {
        String normalized = host.toLowerCase(java.util.Locale.ROOT);
        return normalized.startsWith("www.") ? normalized.substring(4) : normalized;
    }

    private static boolean isValidHttpsUrl(String value) {
        try {
            URI uri = new URI(value.trim());
            return "https".equalsIgnoreCase(uri.getScheme())
                    && uri.getHost() != null && !uri.getHost().isBlank();
        } catch (URISyntaxException | NullPointerException exception) {
            return false;
        }
    }

    private CommercialOffer requireOffer(UUID offerId) {
        return offerRepository.findById(offerId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Offer not found"));
    }

    private static void requireVersion(Long expected, Long actual, String message) {
        if (expected == null || !expected.equals(actual)) {
            throw new ApiException(HttpStatus.CONFLICT, message);
        }
    }

    private PageRequest pageRequest(int page, int size, String sort) {
        return PageRequest.of(Math.max(0, page), Math.max(1, Math.min(MAX_PAGE_SIZE, size)),
                Sort.by("createdAt".equals(sort) ? Sort.Direction.DESC : Sort.Direction.ASC, sort));
    }

    private OfferProviderResponse providerResponse(OfferProvider provider) {
        return new OfferProviderResponse(provider.getId(), provider.getName(), provider.getSector(),
                provider.getOfficialWebsite(), provider.getCountry(), provider.getStatus(),
                provider.getCreatedAt(), provider.getUpdatedAt(), provider.getVersion());
    }

    private AdminEvidenceResponse evidenceResponse(OfferEvidence evidence) {
        return new AdminEvidenceResponse(evidence.getId(), evidence.getOfficialUrl(), evidence.getConsultedAt(),
                evidence.getVerifiedAt(), evidence.getEvidenceScope(), evidence.getNature(),
                evidence.getVerifiedSummary(), evidence.getExpiresAt(), evidence.getVerificationStatus(),
                evidence.getProvenance(), evidence.getCreatedAt(), evidence.getUpdatedAt(),
                evidence.getVerifiedByUserId(), evidence.getVerifiedPricingVersion(),
                evidence.getVerifiedProviderVersion(), evidence.getVersion());
    }

    private OfferPriceHistoryResponse historyResponse(OfferPriceHistory history) {
        return new OfferPriceHistoryResponse(history.getId(), history.getOldAmountMinor(),
                history.getNewAmountMinor(), history.getOldPricingSnapshot(), history.getNewPricingSnapshot(),
                history.getChangedAt(), history.getReason());
    }

    private CatalogAuditResponse auditResponse(CatalogAdminAudit audit) {
        return new CatalogAuditResponse(audit.getId(), audit.getActorUserId(), audit.getAction(),
                audit.getResourceType(), audit.getResourceId(), audit.getDetails(), audit.getOccurredAt(),
                audit.getExecutionMode(), audit.getRequestedByUserId());
    }

    private void audit(UUID actor, String action, String resourceType, UUID resourceId, Map<String, Object> details) {
        audit(CatalogActor.admin(actor), action, resourceType, resourceId, details);
    }

    private void audit(CatalogActor actor, String action, String resourceType, UUID resourceId,
                       Map<String, Object> details) {
        CatalogAdminAudit audit = new CatalogAdminAudit();
        audit.setActorUserId(actor.userId());
        audit.setExecutionMode(actor.mode());
        audit.setRequestedByUserId(actor.requestedByUserId());
        audit.setAction(action);
        audit.setResourceType(resourceType);
        audit.setResourceId(resourceId);
        audit.setDetails(details);
        auditRepository.save(audit);
    }

    private Map<String, Object> providerDetails(OfferProvider provider) {
        return details("name", provider.getName(), "sector", provider.getSector(),
                "officialWebsite", provider.getOfficialWebsite(), "country", provider.getCountry(),
                "status", provider.getStatus());
    }

    private Map<String, Object> offerDetails(CommercialOffer offer) {
        Map<String, Object> values = offerContentDetails(offer);
        values.put("publicationStatus", offer.getPublicationStatus());
        return values;
    }

    private Map<String, Object> offerContentDetails(CommercialOffer offer) {
        return details("commercialName", offer.getCommercialName(), "sector", offer.getSector(),
                "characteristics", offer.getCharacteristics(), "eligibilityConditions", offer.getEligibilityConditions(),
                "commitment", offer.getCommitment(), "territory", offer.getTerritory(),
                "validFrom", offer.getValidFrom(), "validUntil", offer.getValidUntil());
    }

    private Map<String, Object> pricingSnapshot(OfferPricing pricing) {
        return details("amountMinor", pricing.getAmountMinor(), "currency", pricing.getCurrency(),
                "billingPeriod", pricing.getBillingPeriod(), "activationFeeMinor", pricing.getActivationFeeMinor(),
                "otherFees", pricing.getOtherFees(), "promotionPriceType", pricing.getPromotionPriceType(),
                "promotionPercent", pricing.getPromotionPercent(), "priceAfterPromotionMinor",
                pricing.getPriceAfterPromotionMinor(), "promotionDurationMonths", pricing.getPromotionDurationMonths(),
                "promotionValidFrom", pricing.getPromotionValidFrom(), "promotionValidUntil",
                pricing.getPromotionValidUntil(), "discountConditions", pricing.getDiscountConditions(),
                "taxesIncluded", pricing.getTaxesIncluded(), "dataUnknown", pricing.getDataUnknown());
    }

    private Long pricingAmount(Map<String, Object> snapshot) {
        Object value = snapshot.get("amountMinor");
        return value instanceof Number number ? number.longValue() : null;
    }

    private static Map<String, Object> details(Object... pairs) {
        Map<String, Object> details = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            details.put((String) pairs[i], pairs[i + 1]);
        }
        return details;
    }
}
