package com.eparly.api.offer.service;

import com.eparly.api.offer.config.OfferCatalogProperties;
import com.eparly.api.offer.domain.CommercialOffer;
import com.eparly.api.offer.domain.EvidenceStatus;
import com.eparly.api.offer.domain.OfferBillingPeriod;
import com.eparly.api.offer.domain.OfferEvidence;
import com.eparly.api.offer.domain.OfferPricing;
import com.eparly.api.offer.domain.OfferProvider;
import com.eparly.api.offer.domain.OfferSector;
import com.eparly.api.offer.domain.OfferStatus;
import com.eparly.api.offer.domain.ProviderStatus;
import com.eparly.api.offer.repository.OfferEvidenceRepository;
import com.eparly.api.offer.repository.OfferPricingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OfferAvailabilityServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-29T12:00:00Z");

    @Mock private OfferEvidenceRepository evidenceRepository;
    @Mock private OfferPricingRepository pricingRepository;

    private OfferAvailabilityService service;
    private CommercialOffer offer;
    private OfferEvidence currentEvidence;
    private OfferPricing pricing;

    @BeforeEach
    void setUp() {
        OfferCatalogProperties properties = new OfferCatalogProperties();
        properties.setFreshnessThreshold(java.time.Duration.ofDays(30));
        service = new OfferAvailabilityService(evidenceRepository, pricingRepository, properties,
                Clock.fixed(NOW, ZoneOffset.UTC));

        OfferProvider provider = new OfferProvider();
        provider.setSector(OfferSector.STREAMING);
        provider.setStatus(ProviderStatus.ACTIVE);

        offer = new CommercialOffer();
        offer.setId(UUID.randomUUID());
        offer.setProvider(provider);
        offer.setSector(OfferSector.STREAMING);
        offer.setTerritory("FR");
        offer.setPublicationStatus(OfferStatus.VERIFIED);
        offer.setValidFrom(LocalDate.of(2026, 10, 29));
        offer.setValidUntil(LocalDate.of(2026, 10, 29));

        currentEvidence = evidence("https://provider.example/prices", EvidenceStatus.VERIFIED,
                NOW.minusSeconds(60), NOW.minusSeconds(30), null);
        pricing = new OfferPricing();
        pricing.setAmountMinor(999L);
        pricing.setCurrency("EUR");
        pricing.setBillingPeriod(OfferBillingPeriod.MONTHLY);
        pricing.setDataUnknown(false);
    }

    @Test
    void acceptsCurrentlyAvailableOfferAndInclusiveDateBounds() {
        stubCurrentEvidence(currentEvidence);

        OfferAvailabilityService.AvailabilityResult result = service.evaluate(offer, "FR");

        assertThat(result.available()).isTrue();
        assertThat(result.evidence()).isSameAs(currentEvidence);
        assertThat(result.pricing()).isSameAs(pricing);
    }

    @Test
    void rejectsOfferOutsideAvailabilityWindowOrWithInactiveProvider() {
        offer.setValidFrom(LocalDate.of(2026, 10, 30));
        assertThat(service.evaluate(offer, "FR").failure())
                .isEqualTo(OfferAvailabilityService.AvailabilityFailure.NOT_YET_AVAILABLE);

        offer.setValidFrom(LocalDate.of(2026, 10, 28));
        offer.setValidUntil(LocalDate.of(2026, 10, 28));
        assertThat(service.evaluate(offer, "FR").failure())
                .isEqualTo(OfferAvailabilityService.AvailabilityFailure.OFFER_EXPIRED);

        offer.setValidUntil(LocalDate.of(2026, 10, 29));
        offer.getProvider().setStatus(ProviderStatus.INACTIVE);
        assertThat(service.evaluate(offer, "FR").failure())
                .isEqualTo(OfferAvailabilityService.AvailabilityFailure.PROVIDER_INACTIVE);
    }

    @Test
    void newestInvalidEvidenceOverridesAnOlderVerifiedEvidence() {
        OfferEvidence newerInvalidation = evidence("https://provider.example/withdrawn",
                EvidenceStatus.INVALID, NOW.minusSeconds(10), null, null);
        when(evidenceRepository.findAllByOfferIdOrderByConsultedAtDescCreatedAtDescIdDesc(offer.getId()))
                .thenReturn(List.of(newerInvalidation, currentEvidence));

        assertThat(service.evaluate(offer, "FR").failure())
                .isEqualTo(OfferAvailabilityService.AvailabilityFailure.LATEST_EVIDENCE_NOT_VERIFIED);
        verify(evidenceRepository).findAllByOfferIdOrderByConsultedAtDescCreatedAtDescIdDesc(offer.getId());
    }

    @Test
    void rejectsUnknownOrStaleVerificationAndExpiredEvidence() {
        OfferEvidence unknownVerification = evidence("https://provider.example/prices",
                EvidenceStatus.VERIFIED, NOW.minusSeconds(30), null, null);
        stubEvidenceOnly(unknownVerification);
        assertThat(service.evaluate(offer, "FR").failure())
                .isEqualTo(OfferAvailabilityService.AvailabilityFailure.UNKNOWN_VERIFICATION_DATE);

        OfferEvidence stale = evidence("https://provider.example/prices", EvidenceStatus.VERIFIED,
                NOW.minusSeconds(31L * 24 * 60 * 60), NOW.minusSeconds(31L * 24 * 60 * 60), null);
        stubEvidenceOnly(stale);
        assertThat(service.evaluate(offer, "FR").failure())
                .isEqualTo(OfferAvailabilityService.AvailabilityFailure.STALE_EVIDENCE);

        OfferEvidence expired = evidence("https://provider.example/prices", EvidenceStatus.VERIFIED,
                NOW.minusSeconds(30), NOW.minusSeconds(20), NOW);
        stubEvidenceOnly(expired);
        assertThat(service.evaluate(offer, "FR").failure())
                .isEqualTo(OfferAvailabilityService.AvailabilityFailure.EVIDENCE_EXPIRED);
    }

    @Test
    void doesNotAllowUnknownPriceOrInvalidSource() {
        stubCurrentEvidence(currentEvidence);
        pricing.setDataUnknown(true);
        assertThat(service.evaluate(offer, "FR").failure())
                .isEqualTo(OfferAvailabilityService.AvailabilityFailure.UNRELIABLE_PRICING);

        pricing.setDataUnknown(false);
        currentEvidence.setOfficialUrl("https://");
        assertThat(service.evaluate(offer, "FR").failure())
                .isEqualTo(OfferAvailabilityService.AvailabilityFailure.INVALID_EVIDENCE_URL);
    }

    private void stubCurrentEvidence(OfferEvidence evidence) {
        stubEvidenceOnly(evidence);
        when(pricingRepository.findByOfferId(offer.getId())).thenReturn(Optional.of(pricing));
    }

    private void stubEvidenceOnly(OfferEvidence evidence) {
        when(evidenceRepository.findAllByOfferIdOrderByConsultedAtDescCreatedAtDescIdDesc(offer.getId()))
                .thenReturn(List.of(evidence));
    }

    private OfferEvidence evidence(String url, EvidenceStatus status, Instant consultedAt,
                                   Instant verifiedAt, Instant expiresAt) {
        OfferEvidence evidence = new OfferEvidence();
        evidence.setOfficialUrl(url);
        evidence.setVerificationStatus(status);
        evidence.setConsultedAt(consultedAt);
        evidence.setVerifiedAt(verifiedAt);
        evidence.setExpiresAt(expiresAt);
        return evidence;
    }
}
