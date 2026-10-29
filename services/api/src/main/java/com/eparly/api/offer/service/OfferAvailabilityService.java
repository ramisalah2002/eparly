package com.eparly.api.offer.service;

import com.eparly.api.offer.config.OfferCatalogProperties;
import com.eparly.api.offer.domain.CommercialOffer;
import com.eparly.api.offer.domain.EvidenceStatus;
import com.eparly.api.offer.domain.OfferBillingPeriod;
import com.eparly.api.offer.domain.OfferEvidence;
import com.eparly.api.offer.domain.OfferEvidenceScope;
import com.eparly.api.offer.domain.OfferPricing;
import com.eparly.api.offer.domain.OfferSector;
import com.eparly.api.offer.domain.OfferStatus;
import com.eparly.api.offer.domain.ProviderStatus;
import com.eparly.api.offer.repository.OfferEvidenceRepository;
import com.eparly.api.offer.repository.OfferPricingRepository;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class OfferAvailabilityService {

    private final OfferEvidenceRepository evidenceRepository;
    private final OfferPricingRepository pricingRepository;
    private final OfferCatalogProperties properties;
    private final Clock clock;

    public OfferAvailabilityService(OfferEvidenceRepository evidenceRepository,
                                    OfferPricingRepository pricingRepository,
                                    OfferCatalogProperties properties,
                                    Clock clock) {
        this.evidenceRepository = evidenceRepository;
        this.pricingRepository = pricingRepository;
        this.properties = properties;
        this.clock = clock;
    }

    public LocalDate currentDate() {
        return LocalDate.now(clock);
    }

    public Instant currentInstant() {
        return clock.instant();
    }

    public Instant freshnessThreshold() {
        return currentInstant().minus(properties.getFreshnessThreshold());
    }

    public long freshnessThresholdDays() {
        return properties.getFreshnessThreshold().toDays();
    }

    public long promotionFreshnessThresholdDays() {
        return properties.getPromotionFreshnessThreshold().toDays();
    }

    public Specification<CommercialOffer> currentlyAvailable(
            OfferSector sector, UUID providerId, String territory) {
        LocalDate today = currentDate();
        Instant now = currentInstant();
        Instant freshSince = now.minus(properties.getFreshnessThreshold());

        return (root, query, cb) -> {
            var provider = root.join("provider");
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("publicationStatus"), OfferStatus.VERIFIED));
            predicates.add(cb.equal(provider.get("status"), ProviderStatus.ACTIVE));
            predicates.add(cb.equal(provider.get("sector"), root.get("sector")));
            predicates.add(cb.or(cb.isNull(root.get("validFrom")),
                    cb.lessThanOrEqualTo(root.get("validFrom"), today)));
            predicates.add(cb.or(cb.isNull(root.get("validUntil")),
                    cb.greaterThanOrEqualTo(root.get("validUntil"), today)));

            if (sector != null) {
                predicates.add(cb.equal(root.get("sector"), sector));
            }
            if (providerId != null) {
                predicates.add(cb.equal(provider.get("id"), providerId));
            }
            if (territory != null) {
                predicates.add(cb.equal(cb.upper(root.get("territory")), territory.toUpperCase()));
            }

            Subquery<Integer> pricingQuery = query.subquery(Integer.class);
            Root<OfferPricing> pricing = pricingQuery.from(OfferPricing.class);
            pricingQuery.select(cb.literal(1)).where(
                    cb.equal(pricing.get("offer").get("id"), root.get("id")),
                    cb.isNotNull(pricing.get("amountMinor")),
                    cb.greaterThanOrEqualTo(pricing.get("amountMinor"), 0L),
                    cb.isNotNull(pricing.get("currency")),
                    cb.notEqual(cb.trim(pricing.get("currency")), ""),
                    cb.isNotNull(pricing.get("billingPeriod")),
                    cb.notEqual(pricing.get("billingPeriod"), OfferBillingPeriod.UNKNOWN),
                    cb.isFalse(pricing.get("dataUnknown"))
            );
            predicates.add(cb.exists(pricingQuery));

            Subquery<Integer> evidenceQuery = query.subquery(Integer.class);
            Root<OfferEvidence> evidence = evidenceQuery.from(OfferEvidence.class);
            Subquery<Integer> newerEvidenceQuery = evidenceQuery.subquery(Integer.class);
            Root<OfferEvidence> newerEvidence = newerEvidenceQuery.from(OfferEvidence.class);
            Predicate sameOffer = cb.equal(newerEvidence.get("offer").get("id"), root.get("id"));
            Predicate currentEvidenceIsOfferScoped = cb.or(
                    cb.isNull(evidence.get("evidenceScope")),
                    cb.equal(evidence.get("evidenceScope"), OfferEvidenceScope.OFFER));
            Predicate newerEvidenceIsOfferScoped = cb.or(
                    cb.isNull(newerEvidence.get("evidenceScope")),
                    cb.equal(newerEvidence.get("evidenceScope"), OfferEvidenceScope.OFFER));
            Predicate newerConsultation = cb.greaterThan(
                    newerEvidence.get("consultedAt"), evidence.get("consultedAt"));
            Predicate sameConsultation = cb.equal(
                    newerEvidence.get("consultedAt"), evidence.get("consultedAt"));
            Predicate newerCreation = cb.greaterThan(
                    newerEvidence.get("createdAt"), evidence.get("createdAt"));
            Predicate sameCreation = cb.equal(
                    newerEvidence.get("createdAt"), evidence.get("createdAt"));
            Predicate newerId = cb.greaterThan(
                    newerEvidence.<UUID>get("id"), evidence.<UUID>get("id"));
            newerEvidenceQuery.select(cb.literal(1)).where(
                    sameOffer,
                    newerEvidenceIsOfferScoped,
                    cb.or(cb.isNull(newerEvidence.get("consultedAt")),
                            newerConsultation,
                            cb.and(sameConsultation, newerCreation),
                            cb.and(sameConsultation, sameCreation, newerId))
            );

            evidenceQuery.select(cb.literal(1)).where(
                    cb.equal(evidence.get("offer").get("id"), root.get("id")),
                    currentEvidenceIsOfferScoped,
                    cb.equal(evidence.get("verificationStatus"), EvidenceStatus.VERIFIED),
                    cb.isNotNull(evidence.get("verifiedAt")),
                    cb.greaterThanOrEqualTo(evidence.get("verifiedAt"), freshSince),
                    cb.lessThanOrEqualTo(evidence.get("verifiedAt"), now),
                    cb.isNotNull(evidence.get("consultedAt")),
                    cb.lessThanOrEqualTo(evidence.get("consultedAt"), now),
                    cb.greaterThanOrEqualTo(evidence.get("verifiedAt"), evidence.get("consultedAt")),
                    cb.or(cb.isNull(evidence.get("expiresAt")),
                            cb.greaterThan(evidence.get("expiresAt"), now)),
                    cb.isTrue(cb.function("regexp_like", Boolean.class,
                            cb.lower(evidence.get("officialUrl")),
                            cb.literal("^https://[^/?#[:space:]]+([/?#][^[:space:]]*)?$"))),
                    cb.not(cb.exists(newerEvidenceQuery))
            );
            predicates.add(cb.exists(evidenceQuery));
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    public AvailabilityResult evaluate(CommercialOffer offer, String territory) {
        if (offer == null) return AvailabilityResult.unavailable(AvailabilityFailure.OFFER_NOT_FOUND);
        if (offer.getPublicationStatus() != OfferStatus.VERIFIED) {
            return AvailabilityResult.unavailable(AvailabilityFailure.OFFER_NOT_VERIFIED);
        }
        if (offer.getProvider() == null || offer.getProvider().getStatus() != ProviderStatus.ACTIVE) {
            return AvailabilityResult.unavailable(AvailabilityFailure.PROVIDER_INACTIVE);
        }
        if (offer.getSector() == null || offer.getProvider().getSector() != offer.getSector()) {
            return AvailabilityResult.unavailable(AvailabilityFailure.PROVIDER_SECTOR_MISMATCH);
        }

        LocalDate today = currentDate();
        if (offer.getValidFrom() != null && offer.getValidFrom().isAfter(today)) {
            return AvailabilityResult.unavailable(AvailabilityFailure.NOT_YET_AVAILABLE);
        }
        if (offer.getValidUntil() != null && offer.getValidUntil().isBefore(today)) {
            return AvailabilityResult.unavailable(AvailabilityFailure.OFFER_EXPIRED);
        }
        if (territory == null || !territory.equalsIgnoreCase(offer.getTerritory())) {
            return AvailabilityResult.unavailable(AvailabilityFailure.WRONG_TERRITORY);
        }

        List<OfferEvidence> evidenceList =
                evidenceRepository.findAllByOfferIdOrderByConsultedAtDescCreatedAtDescIdDesc(offer.getId());
        List<OfferEvidence> offerEvidenceList = evidenceList.stream()
                .filter(this::isOfferEvidence)
                .toList();
        if (offerEvidenceList.isEmpty()) {
            return AvailabilityResult.unavailable(AvailabilityFailure.MISSING_EVIDENCE);
        }
        OfferEvidence evidence = offerEvidenceList.get(0);
        AvailabilityFailure evidenceFailure = evidenceFailure(evidence, currentInstant());
        if (evidenceFailure != null) return AvailabilityResult.unavailable(evidenceFailure);

        OfferPricing pricing = pricingRepository.findByOfferId(offer.getId()).orElse(null);
        if (!isReliablePricing(pricing)) {
            return AvailabilityResult.unavailable(AvailabilityFailure.UNRELIABLE_PRICING);
        }
        return AvailabilityResult.available(evidence, pricing);
    }

    public Optional<OfferEvidence> currentEvidence(CommercialOffer offer) {
        List<OfferEvidence> evidenceList =
                evidenceRepository.findAllByOfferIdOrderByConsultedAtDescCreatedAtDescIdDesc(offer.getId());
        List<OfferEvidence> offerEvidenceList = evidenceList.stream()
                .filter(this::isOfferEvidence)
                .toList();
        if (offerEvidenceList.isEmpty()
                || evidenceFailure(offerEvidenceList.get(0), currentInstant()) != null) {
            return Optional.empty();
        }
        return Optional.of(offerEvidenceList.get(0));
    }

    private boolean isOfferEvidence(OfferEvidence evidence) {
        return evidence.getEvidenceScope() == null || evidence.getEvidenceScope() == OfferEvidenceScope.OFFER;
    }

    public Optional<OfferEvidence> currentPromotionEvidence(CommercialOffer offer) {
        List<OfferEvidence> evidenceList =
                evidenceRepository.findAllByOfferIdAndEvidenceScopeOrderByConsultedAtDescCreatedAtDescIdDesc(
                        offer.getId(), OfferEvidenceScope.PROMOTION);
        OfferPricing pricing = pricingRepository.findByOfferId(offer.getId()).orElse(null);
        if (evidenceList.isEmpty() || pricing == null
                || !java.util.Objects.equals(evidenceList.get(0).getVerifiedPricingVersion(), pricing.getVersion())
                || !java.util.Objects.equals(evidenceList.get(0).getVerifiedProviderVersion(),
                offer.getProvider().getVersion())
                || evidenceFailure(evidenceList.get(0), currentInstant(),
                properties.getPromotionFreshnessThreshold()) != null) {
            return Optional.empty();
        }
        return Optional.of(evidenceList.get(0));
    }

    public boolean isReliablePricing(OfferPricing pricing) {
        return pricing != null
                && pricing.getAmountMinor() != null
                && pricing.getAmountMinor() >= 0
                && pricing.getCurrency() != null
                && !pricing.getCurrency().isBlank()
                && pricing.getBillingPeriod() != null
                && pricing.getBillingPeriod() != OfferBillingPeriod.UNKNOWN
                && Boolean.FALSE.equals(pricing.getDataUnknown());
    }

    private AvailabilityFailure evidenceFailure(OfferEvidence evidence, Instant now) {
        return evidenceFailure(evidence, now, properties.getFreshnessThreshold());
    }

    private AvailabilityFailure evidenceFailure(OfferEvidence evidence, Instant now,
                                                java.time.Duration freshnessThreshold) {
        if (evidence.getVerificationStatus() != EvidenceStatus.VERIFIED) {
            return AvailabilityFailure.LATEST_EVIDENCE_NOT_VERIFIED;
        }
        if (evidence.getVerifiedAt() == null || evidence.getConsultedAt() == null
                || evidence.getVerifiedAt().isAfter(now)
                || evidence.getConsultedAt().isAfter(now)
                || evidence.getVerifiedAt().isBefore(evidence.getConsultedAt())) {
            return AvailabilityFailure.UNKNOWN_VERIFICATION_DATE;
        }
        if (evidence.getExpiresAt() != null && !evidence.getExpiresAt().isAfter(now)) {
            return AvailabilityFailure.EVIDENCE_EXPIRED;
        }
        if (evidence.getVerifiedAt().isBefore(now.minus(freshnessThreshold))) {
            return AvailabilityFailure.STALE_EVIDENCE;
        }
        if (!isValidHttpsUrl(evidence.getOfficialUrl())) {
            return AvailabilityFailure.INVALID_EVIDENCE_URL;
        }
        return null;
    }

    private static boolean isValidHttpsUrl(String value) {
        if (value == null || value.isBlank()) return false;
        try {
            URI uri = new URI(value.trim());
            return "https".equalsIgnoreCase(uri.getScheme())
                    && uri.getHost() != null && !uri.getHost().isBlank();
        } catch (URISyntaxException e) {
            return false;
        }
    }

    public enum AvailabilityFailure {
        OFFER_NOT_FOUND,
        OFFER_NOT_VERIFIED,
        PROVIDER_INACTIVE,
        PROVIDER_SECTOR_MISMATCH,
        NOT_YET_AVAILABLE,
        OFFER_EXPIRED,
        WRONG_TERRITORY,
        MISSING_EVIDENCE,
        LATEST_EVIDENCE_NOT_VERIFIED,
        UNKNOWN_VERIFICATION_DATE,
        EVIDENCE_EXPIRED,
        STALE_EVIDENCE,
        INVALID_EVIDENCE_URL,
        UNRELIABLE_PRICING
    }

    public record AvailabilityResult(boolean available, OfferEvidence evidence, OfferPricing pricing,
                                     AvailabilityFailure failure) {
        private static AvailabilityResult available(OfferEvidence evidence, OfferPricing pricing) {
            return new AvailabilityResult(true, evidence, pricing, null);
        }

        private static AvailabilityResult unavailable(AvailabilityFailure failure) {
            return new AvailabilityResult(false, null, null, failure);
        }
    }
}
