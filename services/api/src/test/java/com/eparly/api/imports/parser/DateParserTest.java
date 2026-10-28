package com.eparly.api.imports.parser;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DateParserTest {

    @ParameterizedTest(name = "[{0}] avec {1}")
    @DisplayName("Formats francais et ISO valides")
    @CsvSource(delimiter = '|', value = {
            "04/03/2025|dd/MM/yyyy|2025-03-04",
            "04-03-2025|dd-MM-yyyy|2025-03-04",
            "04.03.2025|dd.MM.yyyy|2025-03-04",
            "2025-03-04|yyyy-MM-dd|2025-03-04",
            "2025/03/04|yyyy/MM/dd|2025-03-04",
            "03/04/2025|MM/dd/yyyy|2025-03-04",
            "20250304|yyyyMMdd|2025-03-04",
            "29/02/2024|dd/MM/yyyy|2024-02-29"
    })
    void parsesValidDates(String raw, String pattern, String expected) throws Exception {
        assertThat(DateParser.parse(raw, pattern)).isEqualTo(LocalDate.parse(expected));
    }

    @ParameterizedTest(name = "invalide : [{0}] avec {1}")
    @DisplayName("Dates impossibles ou non conformes refusees (resolution stricte)")
    @CsvSource(delimiter = '|', value = {
            "31/02/2025|dd/MM/yyyy",
            "29/02/2025|dd/MM/yyyy",
            "32/01/2025|dd/MM/yyyy",
            "13/13/2025|dd/MM/yyyy",
            "2025-02-30|yyyy-MM-dd",
            "04/03/25|dd/MM/yyyy",
            "04/03/0025|dd/MM/yyyy",
            "04/03/2025 10:30|dd/MM/yyyy",
            "hier|dd/MM/yyyy",
            "25/03/2025|MM/dd/yyyy"
    })
    void rejectsInvalidDates(String raw, String pattern) {
        assertThatThrownBy(() -> DateParser.parse(raw, pattern)).isInstanceOf(ValueParseException.class);
    }

    @Test
    @DisplayName("Pattern hors liste blanche refuse")
    void rejectsUnsupportedPattern() {
        assertThat(DateParser.isSupported("dd/MM/yyyy")).isTrue();
        assertThat(DateParser.isSupported("EEEE d MMMM")).isFalse();
        assertThatThrownBy(() -> DateParser.parse("04/03/2025", "d/M/y"))
                .isInstanceOfSatisfying(ValueParseException.class,
                        e -> assertThat(e.getCode()).isEqualTo("UNSUPPORTED_DATE_FORMAT"));
    }

    @Test
    @DisplayName("Detection automatique : jour d'abord, jamais mois d'abord")
    void autoDetectionIsDayFirst() {
        assertThat(DateParser.detect(List.of("04/03/2025", "05/03/2025"))).contains("dd/MM/yyyy");
        assertThat(DateParser.detect(List.of("2025-03-04", "2025-03-05"))).contains("yyyy-MM-dd");
        assertThat(DateParser.detect(List.of("04-03-2025"))).contains("dd-MM-yyyy");
        // 03/25/2025 n'est lisible qu'en mois-jour : aucune inversion silencieuse
        assertThat(DateParser.detect(List.of("03/25/2025", "04/25/2025"))).isEmpty();
        assertThat(DateParser.detect(List.of("", "abc"))).isEmpty();
    }

    @Test
    @DisplayName("Detection tolerante a quelques valeurs invalides")
    void autoDetectionToleratesInvalidSamples() {
        assertThat(DateParser.detect(List.of("04/03/2025", "31/02/2025", "06/03/2025"))).contains("dd/MM/yyyy");
    }
}
