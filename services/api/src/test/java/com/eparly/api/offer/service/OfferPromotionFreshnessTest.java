package com.eparly.api.offer.service;

import com.eparly.api.offer.config.OfferCatalogProperties;
import com.eparly.api.offer.domain.EvidenceStatus;
import com.eparly.api.offer.domain.OfferEvidence;
import com.eparly.api.offer.domain.OfferEvidenceScope;
import com.eparly.api.offer.domain.OfferPricing;
import com.eparly.api.offer.domain.OfferProvider;
import com.eparly.api.offer.repository.OfferEvidenceRepository;
import com.eparly.api.offer.repository.OfferPricingRepository;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;

class OfferPromotionFreshnessTest {

    @Test
    void acceptsExactSevenDayBoundaryAndRejectsOneNanosecondOlder() {
        Instant now = Instant.parse("2026-10-29T12:00:00Z");
        Instant cutoff = now.minus(Duration.ofDays(7));

        OfferEvidence atBoundary = evidence(cutoff);
        assertThat(availability(now, atBoundary).currentPromotionEvidence(atBoundary.getOffer()))
                .isPresent();

        OfferEvidence justStale = evidence(cutoff.minusNanos(1));
        assertThat(availability(now, justStale).currentPromotionEvidence(justStale.getOffer()))
                .isEmpty();
    }

    @Test
    void aStaleLatestPromotionProofDoesNotFallBackToAnOlderProof() {
        Instant now = Instant.parse("2026-10-29T12:00:00Z");
        Instant cutoff = now.minus(Duration.ofDays(7));
        OfferEvidence newest = evidence(cutoff.minusSeconds(1));
        OfferEvidence olderButFresh = evidence(now.minus(Duration.ofDays(1)));
        OfferEvidenceRepository repository = mock(OfferEvidenceRepository.class);
        when(repository.findAllByOfferIdAndEvidenceScopeOrderByConsultedAtDescCreatedAtDescIdDesc(
                newest.getOffer().getId(), OfferEvidenceScope.PROMOTION))
                .thenReturn(List.of(newest, olderButFresh));

        assertThat(service(now, repository).currentPromotionEvidence(newest.getOffer())).isEmpty();
    }

    private OfferAvailabilityService availability(Instant now, OfferEvidence evidence) {
        OfferEvidenceRepository repository = mock(OfferEvidenceRepository.class);
        when(repository.findAllByOfferIdAndEvidenceScopeOrderByConsultedAtDescCreatedAtDescIdDesc(
                evidence.getOffer().getId(), OfferEvidenceScope.PROMOTION))
                .thenReturn(List.of(evidence));
        return service(now, repository);
    }

    private OfferAvailabilityService service(Instant now, OfferEvidenceRepository evidenceRepository) {
        OfferCatalogProperties properties = new OfferCatalogProperties();
        properties.setPromotionFreshnessThreshold(Duration.ofDays(7));
        OfferPricingRepository pricingRepository = mock(OfferPricingRepository.class);
        OfferPricing pricing = new OfferPricing();
        pricing.setVersion(1L);
        when(pricingRepository.findByOfferId(any(UUID.class))).thenReturn(Optional.of(pricing));
        return new OfferAvailabilityService(
                evidenceRepository,
                pricingRepository,
                properties,
                Clock.fixed(now, ZoneOffset.UTC));
    }

    private OfferEvidence evidence(Instant verifiedAt) {
        var offer = new com.eparly.api.offer.domain.CommercialOffer();
        offer.setId(UUID.randomUUID());
        OfferProvider provider = new OfferProvider();
        provider.setVersion(1L);
        offer.setProvider(provider);
        OfferEvidence evidence = new OfferEvidence();
        evidence.setOffer(offer);
        evidence.setOfficialUrl("https://example.test/promotion");
        evidence.setEvidenceScope(OfferEvidenceScope.PROMOTION);
        evidence.setVerificationStatus(EvidenceStatus.VERIFIED);
        evidence.setConsultedAt(verifiedAt);
        evidence.setVerifiedAt(verifiedAt);
        evidence.setVerifiedPricingVersion(1L);
        evidence.setVerifiedProviderVersion(1L);
        return evidence;
    }
}
