package com.eparly.api.imports.mapper;

import com.eparly.api.imports.parser.AmountFormat;
import com.eparly.api.imports.parser.CsvFileReader;
import com.eparly.api.imports.parser.ParsedCsv;
import com.eparly.api.imports.service.ImportProperties;
import com.eparly.api.imports.validation.ImportIssue;
import com.eparly.api.imports.validation.RowValidator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ColumnMapperTest {

    private final ImportProperties props = new ImportProperties(5_242_880L, 10_000, 50, 2000, Duration.ofSeconds(30), 20, 100);
    private final CsvFileReader reader = new CsvFileReader(props);
    private final ColumnMapper mapper = new ColumnMapper(List.of(new GenericHeaderProfile()));
    private final RowValidator validator = new RowValidator();

    private ParsedCsv csv(String text) {
        return reader.read(text.getBytes(StandardCharsets.UTF_8), null, null);
    }

    private RowValidator.Result validate(ParsedCsv csv, ImportMapping mapping, String dateFormat) {
        ColumnMapper.Resolution resolution = mapper.resolve(mapping.withDateFormat(dateFormat), csv.headers());
        assertThat(resolution.issues()).isEmpty();
        return validator.validate(csv, resolution.mapping(), "EUR");
    }

    @Test
    @DisplayName("Mapping automatique : synonymes francais, accents et casse ignores, ordre des colonnes libre")
    void suggestsMappingFromFrenchHeaders() {
        ImportMapping suggested = mapper.suggest(
                List.of("Montant", "Libellé de l'opération", "DATE VALEUR", "Date opération", "Solde", "Devise"));

        assertThat(suggested.dateColumn()).isEqualTo("Date opération");
        assertThat(suggested.valueDateColumn()).isEqualTo("DATE VALEUR");
        assertThat(suggested.labelColumn()).isEqualTo("Libellé de l'opération");
        assertThat(suggested.amountColumn()).isEqualTo("Montant");
        assertThat(suggested.currencyColumn()).isEqualTo("Devise");
        assertThat(suggested.debitColumn()).isNull();
        assertThat(suggested.isComplete()).isTrue();
    }

    @Test
    @DisplayName("Mapping automatique debit/credit")
    void suggestsDebitCredit() {
        ImportMapping suggested = mapper.suggest(List.of("Date", "Libelle", "Débit", "Crédit"));

        assertThat(suggested.amountColumn()).isNull();
        assertThat(suggested.debitColumn()).isEqualTo("Débit");
        assertThat(suggested.creditColumn()).isEqualTo("Crédit");
        assertThat(suggested.isComplete()).isTrue();
    }

    @Test
    @DisplayName("En-tetes non reconnus : mapping vide, a completer par l'utilisateur")
    void suggestsNothingForUnknownHeaders() {
        ImportMapping suggested = mapper.suggest(List.of("Col1", "Col2", "Col3"));

        assertThat(suggested.isComplete()).isFalse();
        assertThat(suggested.dateColumn()).isNull();
    }

    @Test
    @DisplayName("Une colonne de reference n'est pas proposee comme identifiant source")
    void doesNotSuggestReferenceAsSourceId() {
        ImportMapping suggested = mapper.suggest(List.of("Date", "Libelle", "Montant", "Reference"));

        assertThat(suggested.sourceIdColumn()).isNull();
    }

    @Test
    @DisplayName("Mapping personnalise : noms d'en-tetes insensibles a la casse et aux accents")
    void resolvesCustomMapping() {
        ParsedCsv csv = csv("Jour;Intitulé;Somme\n04/03/2025;CAFE;-2,50\n");
        ImportMapping custom = new ImportMapping("jour", null, "INTITULE", "somme", null, null, null, null,
                "dd/MM/yyyy", AmountFormat.FR);

        ColumnMapper.Resolution resolution = mapper.resolve(custom, csv.headers());

        assertThat(resolution.valid()).isTrue();
        assertThat(resolution.mapping().dateIndex()).isZero();
        assertThat(resolution.mapping().labelIndex()).isEqualTo(1);
        assertThat(resolution.mapping().amountIndex()).isEqualTo(2);
    }

    @Test
    @DisplayName("Mapping invalide : colonne inconnue, obligatoire manquante, montant et debit/credit melanges, format de date hors liste")
    void rejectsInvalidMappings() {
        List<String> headers = List.of("Date", "Libelle", "Montant", "Debit");

        assertThat(codes(mapper.resolve(new ImportMapping("Date", null, "Libelle", "Inexistante", null, null, null, null, null, null), headers)))
                .contains("MAPPING_COLUMN_NOT_FOUND");
        assertThat(codes(mapper.resolve(new ImportMapping(null, null, "Libelle", "Montant", null, null, null, null, null, null), headers)))
                .contains("MAPPING_REQUIRED_MISSING");
        assertThat(codes(mapper.resolve(new ImportMapping("Date", null, "Libelle", "Montant", "Debit", "Montant", null, null, null, null), headers)))
                .contains("MAPPING_AMOUNT_INVALID");
        assertThat(codes(mapper.resolve(new ImportMapping("Date", null, "Libelle", null, null, null, null, null, null, null), headers)))
                .contains("MAPPING_AMOUNT_INVALID");
        assertThat(codes(mapper.resolve(new ImportMapping("Date", null, "Libelle", null, "Debit", null, null, null, null, null), headers)))
                .contains("MAPPING_AMOUNT_INVALID");
        assertThat(codes(mapper.resolve(new ImportMapping("Date", null, "Date", "Montant", null, null, null, null, null, null), headers)))
                .contains("MAPPING_COLUMN_DUPLICATED");
        assertThat(codes(mapper.resolve(new ImportMapping("Date", null, "Libelle", "Montant", null, null, null, null, "EEEE d MMMM", null), headers)))
                .contains("MAPPING_DATE_FORMAT_UNSUPPORTED");
    }

    @Test
    @DisplayName("En-tete en double dans le fichier : colonne ambigue")
    void rejectsDuplicateHeaders() {
        ColumnMapper.Resolution resolution = mapper.resolve(
                new ImportMapping("Date", null, "Libelle", "Montant", null, null, null, null, null, null),
                List.of("Date", "Libelle", "Montant", "montant"));

        assertThat(codes(resolution)).contains("MAPPING_COLUMN_AMBIGUOUS");
    }

    @Test
    @DisplayName("Validation de lignes : montants francais, dates francaises, libelle normalise, centimes exacts")
    void validatesRowsWithSignedAmount() {
        ParsedCsv csv = csv("Date opération;Date valeur;Libellé;Montant\n"
                + "04/03/2025;05/03/2025;Café  de la Gare ;-45,50\n"
                + "06/03/2025;;Virement reçu;1 250,00\n"
                + "07/03/2025;;Remb. +350;+350,20\n");
        ImportMapping mapping = mapper.suggest(csv.headers());

        RowValidator.Result result = validate(csv, mapping, "dd/MM/yyyy");

        assertThat(result.issues()).isEmpty();
        assertThat(result.rows()).hasSize(3);
        RowValidator.ValidatedRow first = result.rows().get(0);
        assertThat(first.bookingDate()).isEqualTo(LocalDate.of(2025, 3, 4));
        assertThat(first.valueDate()).isEqualTo(LocalDate.of(2025, 3, 5));
        assertThat(first.amountMinor()).isEqualTo(-4550L);
        assertThat(first.currency()).isEqualTo("EUR");
        assertThat(first.label()).isEqualTo("Café  de la Gare");
        assertThat(first.normalizedLabel()).isEqualTo("CAFE DE LA GARE");
        assertThat(result.rows().get(1).amountMinor()).isEqualTo(125000L);
        assertThat(result.rows().get(1).valueDate()).isNull();
        assertThat(result.rows().get(2).amountMinor()).isEqualTo(35020L);
    }

    @Test
    @DisplayName("Colonnes debit/credit : debit negatif, credit positif ; les deux remplis ou aucun -> erreur")
    void validatesDebitCredit() {
        ParsedCsv csv = csv("Date;Libelle;Débit;Crédit\n"
                + "04/03/2025;ACHAT;45,50;\n"
                + "05/03/2025;SALAIRE;;1 500,00\n"
                + "06/03/2025;DEUX;10,00;5,00\n"
                + "07/03/2025;AUCUN;;\n"
                + "08/03/2025;CREDIT NEG;;-3,00\n");
        ImportMapping mapping = mapper.suggest(csv.headers());

        RowValidator.Result result = validate(csv, mapping, "dd/MM/yyyy");

        assertThat(result.rows()).extracting(RowValidator.ValidatedRow::amountMinor).containsExactly(-4550L, 150000L);
        assertThat(result.invalidRows()).isEqualTo(3);
        assertThat(result.issues()).extracting(ImportIssue::code)
                .containsExactly("AMBIGUOUS_DEBIT_CREDIT", "AMOUNT_MISSING", "NEGATIVE_CREDIT");
        assertThat(result.issues()).extracting(ImportIssue::line).containsExactly(4, 5, 6);
    }

    @Test
    @DisplayName("Dates et montants invalides : erreurs avec numero de ligne et colonne, sans la valeur")
    void reportsInvalidDatesAndAmounts() {
        ParsedCsv csv = csv("Date;Libelle;Montant\n"
                + "31/02/2025;A;-1,00\n"
                + "04/03/2025;B;abc\n"
                + "04/03/2025;C;1,250\n"
                + "04/03/2025;D;12,3456\n"
                + "04/03/2025;E;99999999999999999999\n"
                + ";F;-1,00\n"
                + "04/03/2025;;-1,00\n"
                + "04/03/2025;OK;-1,00\n");
        ImportMapping mapping = mapper.suggest(csv.headers());

        RowValidator.Result result = validate(csv, mapping, "dd/MM/yyyy");

        assertThat(result.rows()).hasSize(1);
        assertThat(result.invalidRows()).isEqualTo(7);
        assertThat(result.issues()).extracting(ImportIssue::code).containsExactly(
                "INVALID_DATE", "INVALID_AMOUNT", "AMBIGUOUS_AMOUNT", "TOO_MANY_DECIMALS",
                "AMOUNT_OUT_OF_RANGE", "DATE_MISSING", "LABEL_MISSING");
        assertThat(result.issues()).extracting(ImportIssue::line).containsExactly(2, 3, 4, 5, 6, 7, 8);
        assertThat(result.issues().get(0).column()).isEqualTo("Date");
        assertThat(result.issues().get(1).column()).isEqualTo("Montant");
        assertThat(result.issues()).allSatisfy(issue -> assertThat(issue.message()).doesNotContain("abc").doesNotContain("31/02"));
    }

    @Test
    @DisplayName("Montant nul : avertissement ; ligne trop large : erreur ; devise differente : erreur")
    void reportsWarningsAndStructuralErrors() {
        ParsedCsv csv = csv("Date;Libelle;Montant;Devise\n"
                + "04/03/2025;ZERO;0,00;EUR\n"
                + "05/03/2025;USD;1,00;USD\n"
                + "06/03/2025;TROP;1,00;EUR;extra\n"
                + "07/03/2025;SANS DEVISE;2,00;\n");
        ImportMapping mapping = mapper.suggest(csv.headers());

        RowValidator.Result result = validate(csv, mapping, "dd/MM/yyyy");

        assertThat(result.rows()).extracting(RowValidator.ValidatedRow::label).containsExactly("ZERO", "SANS DEVISE");
        assertThat(result.issues()).extracting(ImportIssue::code)
                .containsExactly("ZERO_AMOUNT", "CURRENCY_MISMATCH", "COLUMN_COUNT_MISMATCH");
        assertThat(result.issues().get(0).severity()).isEqualTo(ImportIssue.Severity.WARNING);
    }

    @Test
    @DisplayName("Un montant positif n'est jamais interprete automatiquement : signe conserve, type deduit plus tard")
    void positiveAmountKeepsItsSignOnly() {
        ParsedCsv csv = csv("Date;Libelle;Montant\n04/03/2025;REMBOURSEMENT OU VIREMENT;45,00\n");
        RowValidator.Result result = validate(csv, mapper.suggest(csv.headers()), "dd/MM/yyyy");

        assertThat(result.rows().get(0).amountMinor()).isEqualTo(4500L);
    }

    @Test
    @DisplayName("Format de date explicite MM/dd/yyyy applique uniquement s'il est demande")
    void explicitMonthFirstFormat() {
        ParsedCsv csv = csv("Date;Libelle;Montant\n03/25/2025;X;-1,00\n");
        ImportMapping mapping = mapper.suggest(csv.headers());

        assertThat(validate(csv, mapping, "dd/MM/yyyy").rows()).isEmpty();
        assertThat(validate(csv, mapping, "MM/dd/yyyy").rows().get(0).bookingDate()).isEqualTo(LocalDate.of(2025, 3, 25));
    }

    @Test
    @DisplayName("Colonne d'identifiant source : renseignee ou absente")
    void readsSourceId() {
        ParsedCsv csv = csv("Date;Libelle;Montant;Id transaction\n04/03/2025;A;-1,00;TX-1\n05/03/2025;B;-2,00;\n");
        ImportMapping mapping = mapper.suggest(csv.headers());

        RowValidator.Result result = validate(csv, mapping, "dd/MM/yyyy");

        assertThat(result.rows().get(0).sourceUid()).isEqualTo("TX-1");
        assertThat(result.rows().get(1).sourceUid()).isNull();
    }

    private static List<String> codes(ColumnMapper.Resolution resolution) {
        return resolution.issues().stream().map(ImportIssue::code).toList();
    }
}
