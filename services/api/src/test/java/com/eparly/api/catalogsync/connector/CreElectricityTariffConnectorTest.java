package com.eparly.api.catalogsync.connector;

import com.eparly.api.catalogsync.domain.CatalogPriceKind;
import com.eparly.api.catalogsync.domain.CatalogSourceType;
import com.eparly.api.catalogsync.domain.CatalogSyncSource;
import com.eparly.api.catalogsync.extraction.RawCatalogOffer;
import com.eparly.api.catalogsync.fetch.OfficialSourceHttpClient;
import com.eparly.api.offer.domain.OfferSector;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class CreElectricityTariffConnectorTest {
    private static final Clock NOVEMBER_2026 =
            Clock.fixed(Instant.parse("2026-11-01T10:00:00Z"), ZoneId.of("Europe/Paris"));
    private static final CatalogSyncSource SOURCE = new CatalogSyncSource(UUID.randomUUID(), null, "CRE Base",
            "cre.fr", "https://www.cre.fr/fileadmin/Documents/Open_data/Marches_de_detail/Option_Base.csv",
            CatalogSourceType.OFFICIAL_API, OfferSector.ELECTRICITY, true, "AUTHORIZED", null, null, "{}",
            CreElectricityTariffConnector.KEY);

    private final CreElectricityTariffConnector connector =
            new CreElectricityTariffConnector(mock(OfficialSourceHttpClient.class), NOVEMBER_2026);

    @Test
    void keepsOnlyTariffsInForceFromTheRealBaseFile() throws IOException {
        List<RawCatalogOffer> offers = connector.parse(fixture("Option_Base"), SOURCE);

        assertThat(offers).extracting(RawCatalogOffer::externalKey).containsExactly(
                "TRV-ELEC:BASE:3", "TRV-ELEC:BASE:6", "TRV-ELEC:BASE:9", "TRV-ELEC:BASE:12", "TRV-ELEC:BASE:15");
        RawCatalogOffer six = offers.get(1);
        assertThat(six.structured().standardAmountMinor()).isEqualTo(19081L);
        assertThat(six.billingPeriod()).isEqualTo("YEARLY");
        assertThat(six.currency()).isEqualTo("EUR");
        assertThat(six.sector()).isEqualTo(OfferSector.ELECTRICITY);
        assertThat(six.structured().priceKind()).isEqualTo(CatalogPriceKind.VARIABLE);
        assertThat(six.structured().priceComponents()).containsEntry("PART_VARIABLE_TTC", "0.2001");
        assertThat(six.characteristics()).containsEntry("tariffOption", "BASE").containsEntry("powerKva", "6");
        assertThat(six.canonicalUrl()).isEqualTo(
                "https://cre.fr/fileadmin/Documents/Open_data/Marches_de_detail/Option_Base.csv");
        assertThat(six.structured().rawPayload()).containsEntry("PART_FIXE_TTC", "190,81");
    }

    @Test
    void keepsExactLongDecimalsWhileRoundingOnlyTheMinorUnitAmount() throws IOException {
        List<RawCatalogOffer> offers = connector.parse(fixture("Option_HPHC"), SOURCE);

        RawCatalogOffer twentyFour = offers.stream()
                .filter(offer -> offer.externalKey().equals("TRV-ELEC:HPHC:24")).findFirst().orElseThrow();
        assertThat(twentyFour.structured().standardAmountMinor()).isEqualTo(47316L);
        assertThat(twentyFour.structured().priceComponents()).containsEntry("PART_FIXE_TTC", "473.1632964");
    }

    @Test
    void detectsEachDocumentedTariffOption() throws IOException {
        assertThat(connector.parse(fixture("Option_Tempo"), SOURCE)).isNotEmpty()
                .allSatisfy(offer -> {
                    assertThat(offer.characteristics()).containsEntry("tariffOption", "TEMPO");
                    assertThat(offer.structured().priceComponents()).containsKey("PART_VARIABLE_HCBleu_TTC");
                });
    }

    @Test
    void historicalRowWithEmptyPricesIsNotEmittedAndNeverBecomesZero() throws IOException {
        String tempo = fixture("Option_Tempo");
        assertThat(tempo).contains("01/08/2016;31/07/2017;6;;;");
        assertThat(connector.parse(tempo, SOURCE)).noneMatch(offer -> offer.externalKey().equals("TRV-ELEC:TEMPO:3"));

        // Scenario synthetique : la meme forme de ligne vide, mais en vigueur.
        String header = tempo.lines().findFirst().orElseThrow();
        List<RawCatalogOffer> offers = connector.parse(header + "\n01/08/2026;;6;;;;;;;;;;;;;;\n", SOURCE);

        assertThat(offers).hasSize(1);
        assertThat(offers.get(0).structured().standardAmountMinor()).isNull();
        assertThat(offers.get(0).structured().priceComponents()).isEmpty();
        assertThat(offers.get(0).characteristics()).doesNotContainKey("kwhPricingKnown");
    }

    @Test
    void acceptsAByteOrderMarkAndRejectsUndocumentedSchemasOrMalformedRows() throws IOException {
        assertThat(connector.parse("﻿" + fixture("Option_Base"), SOURCE)).hasSize(5);

        assertThatThrownBy(() -> connector.parse("A;B;C\n1;2;3\n", SOURCE))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("documented schema");
        String header = fixture("Option_Base").lines().findFirst().orElseThrow();
        assertThatThrownBy(() -> connector.parse(header + "\n01/08/2026;;6\n", SOURCE))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("malformed row");
        assertThatThrownBy(() -> connector.parse(header + "\n2026-08-01;;6;1;1;1;1\n", SOURCE))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("invalid date");
    }

    private static String fixture(String name) throws IOException {
        try (InputStream input = CreElectricityTariffConnectorTest.class
                .getResourceAsStream("/catalog-fixtures/cre/" + name + ".excerpt.csv")) {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
