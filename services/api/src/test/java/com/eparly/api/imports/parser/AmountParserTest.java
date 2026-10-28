package com.eparly.api.imports.parser;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AmountParserTest {

    @ParameterizedTest(name = "[{0}] ({1}) -> {2}")
    @DisplayName("Montants valides convertis exactement en centimes")
    @CsvSource(delimiter = '|', value = {
            "-45,50|AUTO|-4550",
            "1250,00|AUTO|125000",
            "1 250,00|AUTO|125000",
            "1 250.00|AUTO|125000",
            "+350,20|AUTO|35020",
            "1.250,00|AUTO|125000",
            "1,250.00|AUTO|125000",
            "12,5|AUTO|1250",
            "45|AUTO|4500",
            "0,05|AUTO|5",
            "-0,01|FR|-1",
            "1 250,00|FR|125000",
            "1.250,00|FR|125000",
            "1,250.00|EN|125000",
            "1250.50|EN|125050",
            "1,250|EN|125000",
            "-45,50 €|AUTO|-4550",
            "€ 45,50|AUTO|4550",
            "-45,50 EUR|AUTO|-4550",
            "1.250.000|AUTO|125000000",
            "0|AUTO|0"
    })
    void parsesValidAmounts(String raw, AmountFormat format, long expected) throws Exception {
        assertThat(AmountParser.parse(raw, format, "EUR")).isEqualTo(expected);
    }

    @Test
    @DisplayName("Espaces insecables et fins acceptes comme separateurs de milliers")
    void acceptsNoBreakAndThinSpaces() throws Exception {
        assertThat(AmountParser.parse("1\u00A0250,00", AmountFormat.FR, "EUR")).isEqualTo(125000L);
        assertThat(AmountParser.parse("1\u202F250,00", AmountFormat.AUTO, "EUR")).isEqualTo(125000L);
        assertThat(AmountParser.parse("1\u2009250,00 \u20AC", AmountFormat.AUTO, "EUR")).isEqualTo(125000L);
        assertThat(AmountParser.parse("\u2212 45,50", AmountFormat.AUTO, "EUR")).isEqualTo(-4550L);
    }

    @Test
    @DisplayName("Valeur ambigue sans format explicite refusee")
    void rejectsAmbiguousValueInAutoMode() {
        assertThatThrownBy(() -> AmountParser.parse("1,250", AmountFormat.AUTO, "EUR"))
                .isInstanceOfSatisfying(ValueParseException.class, e -> assertThat(e.getCode()).isEqualTo("AMBIGUOUS_AMOUNT"));
        assertThatThrownBy(() -> AmountParser.parse("1.250", AmountFormat.AUTO, "EUR"))
                .isInstanceOfSatisfying(ValueParseException.class, e -> assertThat(e.getCode()).isEqualTo("AMBIGUOUS_AMOUNT"));
        assertThatThrownBy(() -> AmountParser.parse("1.250", AmountFormat.FR, "EUR"))
                .isInstanceOf(ValueParseException.class);
    }

    @Test
    @DisplayName("Decimales incompatibles avec la devise refusees")
    void rejectsTooManyDecimals() {
        assertThatThrownBy(() -> AmountParser.parse("12,345", AmountFormat.FR, "EUR"))
                .isInstanceOfSatisfying(ValueParseException.class, e -> assertThat(e.getCode()).isEqualTo("TOO_MANY_DECIMALS"));
        assertThatThrownBy(() -> AmountParser.parse("0,250", AmountFormat.AUTO, "EUR"))
                .isInstanceOfSatisfying(ValueParseException.class, e -> assertThat(e.getCode()).isEqualTo("TOO_MANY_DECIMALS"));
    }

    @Test
    @DisplayName("Depassement de capacite d'un long refuse")
    void rejectsOverflow() {
        assertThatThrownBy(() -> AmountParser.parse("92233720368547758,08", AmountFormat.FR, "EUR"))
                .isInstanceOfSatisfying(ValueParseException.class, e -> assertThat(e.getCode()).isEqualTo("AMOUNT_OUT_OF_RANGE"));
        assertThatThrownBy(() -> AmountParser.parse("-99999999999999999999,99", AmountFormat.FR, "EUR"))
                .isInstanceOfSatisfying(ValueParseException.class, e -> assertThat(e.getCode()).isEqualTo("AMOUNT_OUT_OF_RANGE"));
    }

    @Test
    @DisplayName("Valeur maximale exacte acceptee")
    void acceptsLongBoundary() throws Exception {
        assertThat(AmountParser.parse("92233720368547758,07", AmountFormat.FR, "EUR")).isEqualTo(Long.MAX_VALUE);
        assertThat(AmountParser.parse("-92233720368547758,08", AmountFormat.FR, "EUR")).isEqualTo(Long.MIN_VALUE);
    }

    @ParameterizedTest(name = "invalide : [{0}]")
    @DisplayName("Textes non numeriques ou mal formes refuses")
    @CsvSource(delimiter = '|', value = {
            "abc", "12a,50", "--5", "1,2,3,4", "12 5", "1 2 50", "45,", ",50", "1.2.3,4", "(45,50)", "45,50-", "NaN", "1e3"
    })
    void rejectsInvalidAmounts(String raw) {
        assertThatThrownBy(() -> AmountParser.parse(raw, AmountFormat.AUTO, "EUR"))
                .isInstanceOf(ValueParseException.class);
    }

    @Test
    @DisplayName("Valeur vide ou nulle refusee")
    void rejectsBlank() {
        assertThatThrownBy(() -> AmountParser.parse("  ", AmountFormat.AUTO, "EUR")).isInstanceOf(ValueParseException.class);
        assertThatThrownBy(() -> AmountParser.parse(null, AmountFormat.AUTO, "EUR")).isInstanceOf(ValueParseException.class);
    }

    @Test
    @DisplayName("Symbole monetaire different de la devise du compte refuse")
    void rejectsForeignCurrencySymbol() throws Exception {
        assertThatThrownBy(() -> AmountParser.parse("45,50 $", AmountFormat.AUTO, "EUR"))
                .isInstanceOfSatisfying(ValueParseException.class, e -> assertThat(e.getCode()).isEqualTo("CURRENCY_MISMATCH"));
        assertThatThrownBy(() -> AmountParser.parse("45,50 USD", AmountFormat.AUTO, "EUR"))
                .isInstanceOf(ValueParseException.class);
        assertThat(AmountParser.parse("45.50 $", AmountFormat.AUTO, "USD")).isEqualTo(4550L);
    }

    @Test
    @DisplayName("Message d'erreur sans valeur financiere")
    void errorMessageDoesNotEchoValue() {
        assertThatThrownBy(() -> AmountParser.parse("123456789abc", AmountFormat.AUTO, "EUR"))
                .hasMessageNotContaining("123456789");
    }
}
