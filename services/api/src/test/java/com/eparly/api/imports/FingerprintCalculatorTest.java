package com.eparly.api.imports;

import com.eparly.api.imports.service.FingerprintCalculator;
import com.eparly.api.imports.validation.LabelNormalizer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class FingerprintCalculatorTest {

    private final FingerprintCalculator calculator = new FingerprintCalculator();
    private final LocalDate date = LocalDate.of(2025, 3, 4);

    @Test
    @DisplayName("Empreinte deterministe de 64 caracteres hexadecimaux")
    void isDeterministicSha256() {
        String base = calculator.contentBase(date, -250L, "EUR", "CAFE DE LA GARE");

        assertThat(calculator.forContent(base, 1)).isEqualTo(calculator.forContent(base, 1)).hasSize(64).matches("[0-9a-f]{64}");
    }

    @Test
    @DisplayName("Deux operations identiques ont des empreintes differentes grace au rang d'occurrence")
    void occurrenceRankSeparatesLegitimateDuplicates() {
        String base = calculator.contentBase(date, -250L, "EUR", "CAFE DE LA GARE");

        assertThat(calculator.forContent(base, 1)).isNotEqualTo(calculator.forContent(base, 2));
    }

    @Test
    @DisplayName("Chaque composante de la base modifie l'empreinte")
    void everyComponentMatters() {
        String reference = calculator.forContent(calculator.contentBase(date, -250L, "EUR", "CAFE"), 1);

        assertThat(calculator.forContent(calculator.contentBase(date.plusDays(1), -250L, "EUR", "CAFE"), 1)).isNotEqualTo(reference);
        assertThat(calculator.forContent(calculator.contentBase(date, -251L, "EUR", "CAFE"), 1)).isNotEqualTo(reference);
        assertThat(calculator.forContent(calculator.contentBase(date, -250L, "USD", "CAFE"), 1)).isNotEqualTo(reference);
        assertThat(calculator.forContent(calculator.contentBase(date, -250L, "EUR", "THE"), 1)).isNotEqualTo(reference);
    }

    @Test
    @DisplayName("Un identifiant source donne une empreinte independante du contenu, distincte d'une empreinte de contenu")
    void sourceUidFingerprintIsIndependentOfContent() {
        String uidFingerprint = calculator.forSourceUid("TX-123");

        assertThat(uidFingerprint).isEqualTo(calculator.forSourceUid("TX-123")).hasSize(64);
        assertThat(uidFingerprint).isNotEqualTo(calculator.forSourceUid("TX-124"));
        assertThat(uidFingerprint).isNotEqualTo(calculator.forContent("TX-123", 1));
    }

    @Test
    @DisplayName("Le libelle normalise rend l'empreinte robuste aux accents, a la casse et aux espaces")
    void normalizedLabelIsStableAcrossExports() {
        assertThat(LabelNormalizer.normalize("Caf\u00e9  de la  gare "))
                .isEqualTo(LabelNormalizer.normalize("CAFE DE LA GARE"))
                .isEqualTo("CAFE DE LA GARE");
        assertThat(LabelNormalizer.normalize("PAIEMENT\u00a0CB")).isEqualTo("PAIEMENT CB");
    }
}
