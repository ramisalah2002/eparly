package com.eparly.api.comparison.service;

import com.eparly.api.comparison.dto.ComparisonRequest;
import com.eparly.api.comparison.service.ComparisonExplanationBuilder;
import com.eparly.api.comparison.strategy.ComparisonStrategy;
import com.eparly.api.comparison.strategy.MobilePlanComparisonStrategy;
import com.eparly.api.comparison.strategy.StreamingComparisonStrategy;
import com.eparly.api.offer.domain.OfferSector;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ComparisonCriteriaValidatorTest {

    private final ComparisonCriteriaValidator validator = new ComparisonCriteriaValidator();
    private final ComparisonStrategy mobile =
            new MobilePlanComparisonStrategy(new ComparisonExplanationBuilder());
    private final ComparisonStrategy streaming =
            new StreamingComparisonStrategy(new ComparisonExplanationBuilder());

    @Test
    void rejectsUnknownMobileAndStreamingCriteriaIncludingUnknownOnlyRequests() {
        assertInvalid(mobile, OfferSector.MOBILE_PLAN, Map.of("unknownMobile", 1));
        assertInvalid(streaming, OfferSector.STREAMING, Map.of("unknownStreaming", true));
    }

    @Test
    void rejectsUnsupportedRoamingCountriesRatherThanSilentlyIgnoringIt() {
        assertInvalid(mobile, OfferSector.MOBILE_PLAN, Map.of("roamingCountries", List.of("FR")));
    }

    @Test
    void rejectsWrongTypesAndOutOfRangeNumbers() {
        assertInvalid(mobile, OfferSector.MOBILE_PLAN, Map.of("requires5G", "true"));
        assertInvalid(mobile, OfferSector.MOBILE_PLAN, Map.of("minDataGb", "50"));
        assertInvalid(mobile, OfferSector.MOBILE_PLAN, Map.of("minDataGb", -1));
        assertInvalid(streaming, OfferSector.STREAMING, Map.of("minScreens", 0));
        assertInvalid(streaming, OfferSector.STREAMING, Map.of("minScreens", 2.5));
        assertInvalid(streaming, OfferSector.STREAMING, Map.of("minVideoQuality", "8K"));
    }

    @Test
    void rejectsMultipleCriteriaWhenAnyOneValueIsInvalid() {
        assertInvalid(mobile, OfferSector.MOBILE_PLAN,
                Map.of("minDataGb", 50, "requires5G", "yes"));
    }

    @Test
    void acceptsCriteriaWithTheirDeclaredTypesAndRanges() {
        validator.validate(request(OfferSector.MOBILE_PLAN,
                Map.of("minDataGb", 0, "requires5G", true)), OfferSector.MOBILE_PLAN, mobile);
        validator.validate(request(OfferSector.STREAMING,
                Map.of("minScreens", 1, "minVideoQuality", "HD")), OfferSector.STREAMING, streaming);
    }

    private void assertInvalid(ComparisonStrategy strategy, OfferSector sector, Map<String, Object> values) {
        assertThatThrownBy(() -> validator.validate(request(sector, values), sector, strategy))
                .isInstanceOf(com.eparly.api.auth.exception.ApiException.class);
    }

    private ComparisonRequest request(OfferSector sector, Map<String, Object> values) {
        return new ComparisonRequest(UUID.randomUUID(), sector, values);
    }
}
