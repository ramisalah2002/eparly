package com.eparly.api.offer.service;

import com.eparly.api.auth.exception.ApiException;
import com.eparly.api.offer.domain.CommercialOffer;
import com.eparly.api.offer.domain.OfferEvidence;
import com.eparly.api.offer.domain.OfferPricing;
import com.eparly.api.offer.domain.OfferPriceHistory;
import com.eparly.api.offer.domain.OfferProvider;
import com.eparly.api.offer.domain.OfferSector;
import com.eparly.api.offer.domain.OfferStatus;
import com.eparly.api.offer.domain.ProviderStatus;
import com.eparly.api.savings.service.PromotionCalculationService;
import com.eparly.api.offer.dto.CommercialOfferResponse;
import com.eparly.api.offer.dto.OfferListItem;
import com.eparly.api.offer.dto.OfferListResponse;
import com.eparly.api.offer.dto.OfferMetadataResponse;
import com.eparly.api.offer.dto.OfferEvidenceResponse;
import com.eparly.api.offer.dto.OfferPricingResponse;
import com.eparly.api.offer.dto.OfferProviderResponse;
import com.eparly.api.offer.repository.CommercialOfferRepository;
import com.eparly.api.offer.repository.OfferEvidenceRepository;
import com.eparly.api.offer.repository.OfferPricingRepository;
import com.eparly.api.offer.repository.OfferPriceHistoryRepository;
import com.eparly.api.offer.repository.OfferProviderRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class OfferCatalogService {

    private final OfferProviderRepository providerRepository;
    private final CommercialOfferRepository offerRepository;
    private final OfferPricingRepository pricingRepository;
    private final OfferEvidenceRepository evidenceRepository;
    private final OfferPriceHistoryRepository priceHistoryRepository;
    private final OfferAvailabilityService availabilityService;
    private final PromotionCalculationService promotionService;

    public OfferCatalogService(OfferProviderRepository providerRepository,
                               CommercialOfferRepository offerRepository,
                               OfferPricingRepository pricingRepository,
                               OfferEvidenceRepository evidenceRepository,
                               OfferPriceHistoryRepository priceHistoryRepository,
                               OfferAvailabilityService availabilityService,
                               PromotionCalculationService promotionService) {
        this.providerRepository = providerRepository;
        this.offerRepository = offerRepository;
        this.pricingRepository = pricingRepository;
        this.evidenceRepository = evidenceRepository;
        this.priceHistoryRepository = priceHistoryRepository;
        this.availabilityService = availabilityService;
        this.promotionService = promotionService;
    }

    /* ---------- Providers ---------- */

    @Transactional
    public OfferProviderResponse createProvider(String name, OfferSector sector,
                                                String officialWebsite, String country) {
        if (name == null || name.trim().isEmpty() || name.length() > 200) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Provider name is required (1-200 chars)");
        }
        if (officialWebsite == null || !isValidHttpsUrl(officialWebsite)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Official website must be a valid HTTPS URL");
        }
        if (providerRepository.existsByNameAndSector(name.trim(), sector)) {
            throw new ApiException(HttpStatus.CONFLICT, "Provider already exists for this sector");
        }
        OfferProvider p = new OfferProvider();
        p.setName(name.trim());
        p.setSector(sector);
        p.setOfficialWebsite(officialWebsite.trim());
        p.setCountry(country == null || country.trim().isEmpty() ? "FR" : country.trim().toUpperCase());
        p.setStatus(ProviderStatus.ACTIVE);
        OfferProvider saved = providerRepository.saveAndFlush(p);
        return toProviderResponse(saved);
    }

    /* ---------- Offers ---------- */

    @Transactional
    public CommercialOfferResponse createDraftOffer(UUID providerId, String commercialName, OfferSector sector,
                                                    String characteristics, String eligibilityConditions,
                                                    String commitment, String territory,
                                                    LocalDate validFrom, LocalDate validUntil) {
        OfferProvider provider = providerRepository.findByIdAndStatus(providerId, ProviderStatus.ACTIVE)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Provider not found or inactive"));
        if (provider.getSector() != sector) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Offer sector must match provider sector");
        }
        if (commercialName == null || commercialName.trim().isEmpty() || commercialName.length() > 200) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Commercial name is required (1-200 chars)");
        }
        if (offerRepository.existsByCommercialNameAndProviderId(commercialName.trim(), providerId)) {
            throw new ApiException(HttpStatus.CONFLICT, "Offer already exists for this provider");
        }
        CommercialOffer o = new CommercialOffer();
        o.setProvider(provider);
        o.setCommercialName(commercialName.trim());
        o.setSector(sector);
        o.setCharacteristics(characteristics);
        o.setEligibilityConditions(eligibilityConditions);
        o.setCommitment(commitment);
        o.setTerritory(territory == null || territory.trim().isEmpty() ? "FR" : territory.trim().toUpperCase());
        o.setPublicationStatus(OfferStatus.DRAFT);
        o.setValidFrom(validFrom);
        o.setValidUntil(validUntil);
        CommercialOffer saved = offerRepository.saveAndFlush(o);
        return toDetailResponse(saved);
    }

    @Transactional
    public CommercialOfferResponse validateAndPublish(UUID offerId) {
        CommercialOffer o = offerRepository.findById(offerId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Offer not found"));
        if (o.getPublicationStatus() == OfferStatus.ARCHIVED) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Cannot publish an archived offer");
        }
        if (o.getProvider().getStatus() != ProviderStatus.ACTIVE
                || o.getProvider().getSector() != o.getSector()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Offer provider is not active for this sector");
        }
        // Require pricing with reliable amount
        OfferPricing pricing = pricingRepository.findByOfferId(offerId)
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST,
                        "Publication requires reliable pricing information"));
        if (!availabilityService.isReliablePricing(pricing)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Publication requires a valid recurring amount");
        }
        if (o.getValidFrom() != null && o.getValidFrom().isAfter(availabilityService.currentDate())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Offer is not available yet");
        }
        if (o.getValidUntil() != null && o.getValidUntil().isBefore(availabilityService.currentDate())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Offer validity has expired");
        }
        requirePublishableData(o, pricing);
        OfferEvidence evidence = availabilityService.currentEvidence(o)
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST,
                        "Publication requires a current, verified official source"));
        if (!pointsToSpecificPage(evidence.getOfficialUrl())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Publication requires evidence from the offer's own page");
        }
        o.setPublicationStatus(OfferStatus.VERIFIED);
        CommercialOffer saved = offerRepository.saveAndFlush(o);
        return toDetailResponse(saved, List.of(evidence));
    }

    @Transactional
    public void markStale() {
        for (CommercialOffer offer : offerRepository.findByPublicationStatus(OfferStatus.VERIFIED)) {
            if (availabilityService.currentEvidence(offer).isEmpty()) {
                offer.setPublicationStatus(OfferStatus.STALE);
                offerRepository.save(offer);
            }
        }
    }

    @Transactional
    public void markExpired() {
        LocalDate now = availabilityService.currentDate();
        List<CommercialOffer> expired = offerRepository.findByPublicationStatus(OfferStatus.VERIFIED).stream()
                .filter(o -> o.getValidUntil() != null && o.getValidUntil().isBefore(now))
                .toList();
        for (CommercialOffer o : expired) {
            o.setPublicationStatus(OfferStatus.EXPIRED);
            offerRepository.save(o);
        }
    }

    @Transactional
    public void archive(UUID offerId) {
        CommercialOffer o = offerRepository.findById(offerId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Offer not found"));
        o.setPublicationStatus(OfferStatus.ARCHIVED);
        offerRepository.save(o);
    }

    @Transactional
    public void revisePrice(UUID offerId, Long newAmountMinor, String reason) {
        if (newAmountMinor == null || newAmountMinor < 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Price must be a non-negative amount in minor units");
        }
        CommercialOffer o = offerRepository.findById(offerId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Offer not found"));
        OfferPricing pricing = pricingRepository.findByOfferId(offerId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Pricing not found"));
        Long old = pricing.getAmountMinor();
        if (old != null && old.equals(newAmountMinor)) return;
        OfferPriceHistory h = new OfferPriceHistory();
        h.setOffer(o);
        h.setOldAmountMinor(old);
        h.setNewAmountMinor(newAmountMinor);
        h.setReason(reason);
        h.setOldPricingSnapshot(pricingSnapshot(pricing));
        pricing.setAmountMinor(newAmountMinor);
        pricingRepository.save(pricing);
        h.setNewPricingSnapshot(pricingSnapshot(pricing));
        priceHistoryRepository.save(h);
        // Re-evaluate publication status after price change
        if (o.getPublicationStatus() == OfferStatus.VERIFIED) {
            o.setPublicationStatus(OfferStatus.DRAFT);
            offerRepository.save(o);
        }
    }

    /* ---------- Public read operations ---------- */

    public OfferListResponse listPublicOffers(OfferSector sector, UUID providerId, String territory,
                                              OfferStatus status, int page, int size) {
        if (size > 100) size = 100;
        if (size < 1) size = 20;
        if (page < 0) page = 0;
        String effectiveTerritory = territory == null || territory.isBlank()
                ? "FR" : territory.trim().toUpperCase();
        PageRequest pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());

        if (status != null && status != OfferStatus.VERIFIED) {
            Page<CommercialOffer> emptyPage = new PageImpl<>(List.of(), pageable, 0);
            return new OfferListResponse(List.of(), emptyPage.getNumber(), emptyPage.getSize(), 0, 0);
        }
        Page<CommercialOffer> pageResult = offerRepository.findAll(
                availabilityService.currentlyAvailable(sector, providerId, effectiveTerritory), pageable);
        List<OfferListItem> items = pageResult.getContent().stream()
                .map(this::toListItem)
                .toList();
        return new OfferListResponse(items, pageResult.getNumber(), pageResult.getSize(),
                pageResult.getTotalElements(), pageResult.getTotalPages());
    }

    public CommercialOfferResponse getPublicOffer(UUID offerId) {
        CommercialOffer o = offerRepository.findById(offerId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Offer not found or not publicly available"));
        OfferAvailabilityService.AvailabilityResult result = availabilityService.evaluate(o, "FR");
        if (!result.available()) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Offer not found or not publicly available");
        }
        return toDetailResponse(o, List.of(result.evidence()));
    }

    public CommercialOfferResponse getAdminOffer(UUID offerId) {
        CommercialOffer offer = offerRepository.findById(offerId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Offer not found"));
        List<OfferEvidence> evidence =
                evidenceRepository.findAllByOfferIdOrderByConsultedAtDescCreatedAtDescIdDesc(offerId);
        return toDetailResponse(offer, evidence, true);
    }

    public List<OfferProvider> listAllProviders() {
        return providerRepository.findByStatus(ProviderStatus.ACTIVE).stream().sorted(
                (a, b) -> a.getName().compareToIgnoreCase(b.getName())
        ).toList();
    }

    public List<OfferProvider> listProvidersBySector(OfferSector sector) {
        return providerRepository.findBySectorAndStatus(sector, ProviderStatus.ACTIVE);
    }

    public OfferMetadataResponse getMetadata() {
        return new OfferMetadataResponse(
                List.of("STREAMING", "MOBILE_PLAN", "INTERNET_BOX"),
                List.of("WEEKLY", "MONTHLY", "QUARTERLY", "YEARLY", "UNKNOWN"),
                List.of("VERIFIED"),
                List.of("FR"),
                "EUR",
                true,
                "Le catalogue n'est pas exhaustif. Il reference uniquement les offres verifiees manuellement."
        );
    }

    /* ---------- Mappers ---------- */

    private OfferProviderResponse toProviderResponse(OfferProvider p) {
        return new OfferProviderResponse(
                p.getId(), p.getName(), p.getSector(), p.getOfficialWebsite(),
                p.getCountry(), p.getStatus(), p.getCreatedAt(), p.getUpdatedAt(), p.getVersion()
        );
    }

    private OfferListItem toListItem(CommercialOffer o) {
        OfferPricing pricing = pricingRepository.findByOfferId(o.getId()).orElse(null);
        return new OfferListItem(
                o.getId(), o.getProvider().getId(), o.getProvider().getName(),
                o.getCommercialName(), o.getSector(), o.getTerritory(), o.getPublicationStatus(),
                pricing != null ? pricing.getAmountMinor() : null,
                pricing != null ? pricing.getCurrency() : null,
                pricing != null ? pricing.getBillingPeriod().name() : null,
                o.getValidFrom(), o.getValidUntil(), o.getCreatedAt(), o.getUpdatedAt()
        );
    }

    private CommercialOfferResponse toDetailResponse(CommercialOffer o) {
        OfferPricing pricing = pricingRepository.findByOfferId(o.getId()).orElse(null);
        List<OfferEvidence> evidence =
                evidenceRepository.findAllByOfferIdOrderByConsultedAtDescCreatedAtDescIdDesc(o.getId());
        return toDetailResponse(o, evidence);
    }

    private CommercialOfferResponse toDetailResponse(CommercialOffer o, List<OfferEvidence> evidence) {
        return toDetailResponse(o, evidence, false);
    }

    private CommercialOfferResponse toDetailResponse(
            CommercialOffer o, List<OfferEvidence> evidence, boolean includeUnverifiedPromotion
    ) {
        OfferPricing pricing = pricingRepository.findByOfferId(o.getId()).orElse(null);
        List<OfferEvidence> responseEvidence = new java.util.ArrayList<>(evidence);
        OfferAvailabilityService.AvailabilityResult availability =
                availabilityService.evaluate(o, o.getTerritory());
        if (availability.available()) {
            availabilityService.currentPromotionEvidence(o).ifPresent(promotionEvidence -> {
                if (promotionService.isPromotionApplicable(
                        pricing, o, availabilityService.currentDate(), promotionEvidence)
                        && responseEvidence.stream().noneMatch(promotionEvidence::equals)) {
                    responseEvidence.add(promotionEvidence);
                }
            });
        }
        return new CommercialOfferResponse(
                o.getId(),
                toProviderResponse(o.getProvider()),
                o.getCommercialName(), o.getSector(), o.getCharacteristics(),
                o.getEligibilityConditions(), o.getCommitment(), o.getTerritory(),
                o.getPublicationStatus(), o.getValidFrom(), o.getValidUntil(),
                pricing != null ? toPricingResponse(pricing, o, includeUnverifiedPromotion) : null,
                responseEvidence.stream().map(this::toEvidenceResponse).toList(),
                o.getCreatedAt(), o.getUpdatedAt(), o.getVersion()
        );
    }

    private OfferPricingResponse toPricingResponse(
            OfferPricing p, CommercialOffer offer, boolean includeUnverifiedPromotion
    ) {
        boolean offerAvailable = availabilityService.evaluate(offer, offer.getTerritory()).available();
        boolean promotionVerified = offerAvailable && availabilityService.currentPromotionEvidence(offer)
                .filter(evidence -> promotionService.isPromotionApplicable(
                        p, offer, availabilityService.currentDate(), evidence))
                .isPresent();
        return new OfferPricingResponse(
                p.getId(), p.getAmountMinor(), p.getCurrency(), p.getBillingPeriod(),
                p.getActivationFeeMinor(), p.getOtherFees(),
                promotionVerified || includeUnverifiedPromotion ? p.getPromotionPercent() : null,
                promotionVerified || includeUnverifiedPromotion ? p.getPromotionDurationMonths() : null,
                promotionVerified || includeUnverifiedPromotion ? p.getPriceAfterPromotionMinor() : null,
                p.getDiscountConditions(), p.getTaxesIncluded(), p.getDataUnknown(),
                p.getCreatedAt(), p.getUpdatedAt(),
                promotionVerified || includeUnverifiedPromotion ? p.getPromotionPriceType() : null,
                promotionVerified || includeUnverifiedPromotion ? p.getPromotionValidFrom() : null,
                promotionVerified || includeUnverifiedPromotion ? p.getPromotionValidUntil() : null,
                promotionVerified, p.getVersion()
        );
    }

    private OfferEvidenceResponse toEvidenceResponse(OfferEvidence e) {
        return new OfferEvidenceResponse(
                e.getId(), e.getOfficialUrl(), e.getConsultedAt(), e.getVerifiedAt(),
                e.getEvidenceScope(), e.getNature(), e.getVerifiedSummary(), e.getExpiresAt(),
                e.getVerificationStatus(), e.getProvenance(),
                e.getCreatedAt(), e.getUpdatedAt()
        );
    }

    private Map<String, Object> pricingSnapshot(OfferPricing pricing) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("amountMinor", pricing.getAmountMinor());
        snapshot.put("currency", pricing.getCurrency());
        snapshot.put("billingPeriod", pricing.getBillingPeriod());
        snapshot.put("activationFeeMinor", pricing.getActivationFeeMinor());
        snapshot.put("otherFees", pricing.getOtherFees());
        snapshot.put("promotionPriceType", pricing.getPromotionPriceType());
        snapshot.put("promotionPercent", pricing.getPromotionPercent());
        snapshot.put("promotionPriceMinor", pricing.getPriceAfterPromotionMinor());
        snapshot.put("promotionDurationMonths", pricing.getPromotionDurationMonths());
        snapshot.put("promotionValidFrom", pricing.getPromotionValidFrom());
        snapshot.put("promotionValidUntil", pricing.getPromotionValidUntil());
        snapshot.put("discountConditions", pricing.getDiscountConditions());
        snapshot.put("taxesIncluded", pricing.getTaxesIncluded());
        snapshot.put("dataUnknown", pricing.getDataUnknown());
        return snapshot;
    }

    private static boolean isValidHttpsUrl(String url) {
        try {
            URI uri = new URI(url.trim());
            return "https".equalsIgnoreCase(uri.getScheme())
                    && uri.getHost() != null && !uri.getHost().isBlank()
                    && uri.getPath() != null;
        } catch (URISyntaxException e) {
            return false;
        }
    }

    /** Une donnee inconnue n'est jamais assimilee a zero ou a une absence : elle bloque la publication. */
    private static void requirePublishableData(CommercialOffer offer, OfferPricing pricing) {
        if (offer.getReuseRightsStatus() != com.eparly.api.offer.domain.OfferReuseRights.ESTABLISHED) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Publication requires established reuse rights");
        }
        if (pricing.getActivationFeeMinor() == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Publication requires known activation fees");
        }
        if (offer.getCommitment() == null || offer.getCommitment().isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Publication requires a confirmed commitment");
        }
        boolean promotionDeclared = pricing.getPromotionPercent() != null
                || pricing.getPromotionDurationMonths() != null || pricing.getPriceAfterPromotionMinor() != null
                || pricing.getPromotionValidFrom() != null || pricing.getPromotionValidUntil() != null;
        boolean promotionComplete = pricing.getPromotionPriceType() != null
                && pricing.getPromotionDurationMonths() != null && pricing.getPromotionDurationMonths() > 0
                && switch (pricing.getPromotionPriceType()) {
                    case FIXED_PRICE -> pricing.getPriceAfterPromotionMinor() != null;
                    case PERCENTAGE -> pricing.getPromotionPercent() != null;
                };
        if ((promotionDeclared || pricing.getPromotionPriceType() != null) && !promotionComplete) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Publication requires a complete promotion");
        }
    }

    private static boolean pointsToSpecificPage(String url) {
        try {
            String path = java.net.URI.create(url.trim()).getPath();
            return path != null && !path.isBlank() && !path.equals("/");
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }
}
