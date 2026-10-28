package com.eparly.api.offer.service;

import com.eparly.api.auth.exception.ApiException;
import com.eparly.api.offer.config.OfferCatalogProperties;
import com.eparly.api.offer.domain.CommercialOffer;
import com.eparly.api.offer.domain.EvidenceStatus;
import com.eparly.api.offer.domain.OfferBillingPeriod;
import com.eparly.api.offer.domain.OfferEvidence;
import com.eparly.api.offer.domain.OfferPricing;
import com.eparly.api.offer.domain.OfferPriceHistory;
import com.eparly.api.offer.domain.OfferProvider;
import com.eparly.api.offer.domain.OfferSector;
import com.eparly.api.offer.domain.OfferStatus;
import com.eparly.api.offer.domain.ProviderStatus;
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
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class OfferCatalogService {

    private final OfferProviderRepository providerRepository;
    private final CommercialOfferRepository offerRepository;
    private final OfferPricingRepository pricingRepository;
    private final OfferEvidenceRepository evidenceRepository;
    private final OfferPriceHistoryRepository priceHistoryRepository;
    private final OfferCatalogProperties properties;

    public OfferCatalogService(OfferProviderRepository providerRepository,
                               CommercialOfferRepository offerRepository,
                               OfferPricingRepository pricingRepository,
                               OfferEvidenceRepository evidenceRepository,
                               OfferPriceHistoryRepository priceHistoryRepository,
                               OfferCatalogProperties properties) {
        this.providerRepository = providerRepository;
        this.offerRepository = offerRepository;
        this.pricingRepository = pricingRepository;
        this.evidenceRepository = evidenceRepository;
        this.priceHistoryRepository = priceHistoryRepository;
        this.properties = properties;
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
        // Require pricing with reliable amount
        OfferPricing pricing = pricingRepository.findByOfferId(offerId)
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST,
                        "Publication requires reliable pricing information"));
        if (pricing.getAmountMinor() == null || pricing.getAmountMinor() < 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Publication requires a valid recurring amount");
        }
        if (pricing.getBillingPeriod() == null || pricing.getBillingPeriod() == OfferBillingPeriod.UNKNOWN) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Publication requires a known billing period");
        }
        // Require verified evidence with HTTPS URL
        OfferEvidence evidence = evidenceRepository.findByOfferIdAndVerificationStatus(offerId, EvidenceStatus.VERIFIED)
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST,
                        "Publication requires at least one verified official source"));
        if (evidence.getOfficialUrl() == null || !isValidHttpsUrl(evidence.getOfficialUrl())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Verified evidence must reference a valid HTTPS URL");
        }
        // Validity
        LocalDate now = LocalDate.now();
        if (o.getValidUntil() != null && !o.getValidUntil().isAfter(now)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Offer validity must be in the future");
        }
        o.setPublicationStatus(OfferStatus.VERIFIED);
        CommercialOffer saved = offerRepository.saveAndFlush(o);
        return toDetailResponse(saved);
    }

    @Transactional
    public void markStale() {
        Instant threshold = Instant.now().minus(properties.getFreshnessThreshold());
        List<OfferEvidence> staleEvidence = evidenceRepository.findExpiredEvidence(EvidenceStatus.VERIFIED, threshold);
        for (OfferEvidence e : staleEvidence) {
            CommercialOffer o = e.getOffer();
            if (o.getPublicationStatus() == OfferStatus.VERIFIED) {
                o.setPublicationStatus(OfferStatus.STALE);
                offerRepository.save(o);
            }
        }
    }

    @Transactional
    public void markExpired() {
        LocalDate now = LocalDate.now();
        List<CommercialOffer> expired = offerRepository.findByPublicationStatus(OfferStatus.VERIFIED).stream()
                .filter(o -> o.getValidUntil() != null && !o.getValidUntil().isAfter(now))
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
        priceHistoryRepository.save(h);
        pricing.setAmountMinor(newAmountMinor);
        pricingRepository.save(pricing);
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
        PageRequest pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());

        // Only publicly consultable statuses
        OfferStatus publicStatus = (status == null || status == OfferStatus.DRAFT || status == OfferStatus.ARCHIVED)
                ? OfferStatus.VERIFIED : status;

        Specification<CommercialOffer> spec = Specification.where((root, query, cb) -> cb.equal(root.get("publicationStatus"), publicStatus));
        if (sector != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("sector"), sector));
        }
        if (providerId != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("provider").get("id"), providerId));
        }
        if (territory != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("territory"), territory));
        }
        // Exclude offers whose validity has expired
        LocalDate now = LocalDate.now();
        spec = spec.and((root, query, cb) -> cb.or(
                cb.isNull(root.get("validUntil")),
                cb.greaterThanOrEqualTo(root.get("validUntil"), now)
        ));

        Page<CommercialOffer> pageResult = offerRepository.findAll(spec, pageable);
        List<OfferListItem> items = pageResult.getContent().stream()
                .map(this::toListItem)
                .toList();
        return new OfferListResponse(items, pageResult.getNumber(), pageResult.getSize(),
                pageResult.getTotalElements(), pageResult.getTotalPages());
    }

    public CommercialOfferResponse getPublicOffer(UUID offerId) {
        CommercialOffer o = offerRepository.findByIdAndPublicationStatus(offerId, OfferStatus.VERIFIED)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Offer not found or not publicly available"));
        return toDetailResponse(o);
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
                p.getCountry(), p.getStatus(), p.getCreatedAt(), p.getUpdatedAt()
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
        List<OfferEvidence> evidence = evidenceRepository.findAllByOfferIdOrderByVerifiedAtDesc(o.getId());
        return new CommercialOfferResponse(
                o.getId(),
                toProviderResponse(o.getProvider()),
                o.getCommercialName(), o.getSector(), o.getCharacteristics(),
                o.getEligibilityConditions(), o.getCommitment(), o.getTerritory(),
                o.getPublicationStatus(), o.getValidFrom(), o.getValidUntil(),
                pricing != null ? toPricingResponse(pricing) : null,
                evidence.stream().map(this::toEvidenceResponse).toList(),
                o.getCreatedAt(), o.getUpdatedAt()
        );
    }

    private OfferPricingResponse toPricingResponse(OfferPricing p) {
        return new OfferPricingResponse(
                p.getId(), p.getAmountMinor(), p.getCurrency(), p.getBillingPeriod(),
                p.getActivationFeeMinor(), p.getOtherFees(), p.getPromotionPercent(),
                p.getPromotionDurationMonths(), p.getPriceAfterPromotionMinor(),
                p.getDiscountConditions(), p.getTaxesIncluded(), p.getDataUnknown(),
                p.getCreatedAt(), p.getUpdatedAt()
        );
    }

    private OfferEvidenceResponse toEvidenceResponse(OfferEvidence e) {
        return new OfferEvidenceResponse(
                e.getId(), e.getOfficialUrl(), e.getConsultedAt(), e.getVerifiedAt(),
                e.getNature(), e.getVerifiedSummary(), e.getExpiresAt(),
                e.getVerificationStatus(), e.getProvenance(),
                e.getCreatedAt(), e.getUpdatedAt()
        );
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
}
