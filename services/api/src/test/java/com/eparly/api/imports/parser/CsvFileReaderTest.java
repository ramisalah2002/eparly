package com.eparly.api.imports.parser;

import com.eparly.api.imports.service.ImportProperties;
import com.eparly.api.imports.validation.FileValidator;
import com.eparly.api.imports.validation.ImportFileException;
import com.eparly.api.imports.validation.ImportFileException.Code;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CsvFileReaderTest {

    private static ImportProperties props(int maxRows, int maxColumns, int maxField, Duration timeout) {
        return new ImportProperties(5_242_880L, maxRows, maxColumns, maxField, timeout, 20, 100);
    }

    private final ImportProperties defaults = props(10_000, 50, 2000, Duration.ofSeconds(30));
    private final CsvFileReader reader = new CsvFileReader(defaults);
    private final FileValidator validator = new FileValidator(defaults);

    private static byte[] utf8(String text) {
        return text.getBytes(StandardCharsets.UTF_8);
    }

    @Test
    @DisplayName("CSV UTF-8 avec point-virgule")
    void readsUtf8Semicolon() {
        ParsedCsv csv = reader.read(utf8("Date;Libellé;Montant\n04/03/2025;CAFÉ;-2,50\n05/03/2025;PAIN;-1,20\n"), null, null);

        assertThat(csv.charset()).isEqualTo(CsvCharset.UTF_8);
        assertThat(csv.charsetAmbiguous()).isFalse();
        assertThat(csv.delimiter()).isEqualTo(';');
        assertThat(csv.headers()).containsExactly("Date", "Libellé", "Montant");
        assertThat(csv.rows()).hasSize(2);
        assertThat(csv.rows().get(0).values()).containsExactly("04/03/2025", "CAFÉ", "-2,50");
        assertThat(csv.rows().get(0).line()).isEqualTo(2);
        assertThat(csv.rows().get(1).line()).isEqualTo(3);
    }

    @Test
    @DisplayName("CSV UTF-8 avec BOM : le BOM n'apparait pas dans le premier en-tete")
    void readsUtf8Bom() {
        byte[] content = utf8("Date;Libellé;Montant\n04/03/2025;CAFÉ;-2,50\n");
        byte[] withBom = new byte[content.length + 3];
        withBom[0] = (byte) 0xEF;
        withBom[1] = (byte) 0xBB;
        withBom[2] = (byte) 0xBF;
        System.arraycopy(content, 0, withBom, 3, content.length);

        ParsedCsv csv = reader.read(withBom, null, null);

        assertThat(csv.charset()).isEqualTo(CsvCharset.UTF_8);
        assertThat(csv.charsetAmbiguous()).isFalse();
        assertThat(csv.headers().get(0)).isEqualTo("Date");
        assertThat(csv.rows().get(0).values().get(1)).isEqualTo("CAFÉ");
    }

    @Test
    @DisplayName("CSV avec virgule comme separateur")
    void readsComma() {
        ParsedCsv csv = reader.read(utf8("Date,Libelle,Montant\n2025-03-04,CAFE,-2.50\n"), null, null);

        assertThat(csv.delimiter()).isEqualTo(',');
        assertThat(csv.headers()).containsExactly("Date", "Libelle", "Montant");
        assertThat(csv.rows().get(0).values()).containsExactly("2025-03-04", "CAFE", "-2.50");
    }

    @Test
    @DisplayName("Champs entre guillemets : separateur, guillemets doubles et retours a la ligne conserves, numeros de ligne exacts")
    void readsQuotedFieldsWithEmbeddedDelimiterAndNewline() {
        String text = "Date;Libelle;Montant\r\n"
                + "04/03/2025;\"PAIEMENT; CB \"\"X\"\"\";\"-2,50\"\r\n"
                + "05/03/2025;\"LIGNE 1\r\nLIGNE 2\";-1,00\r\n"
                + "06/03/2025;APRES;-3,00\r\n";

        ParsedCsv csv = reader.read(utf8(text), null, null);

        assertThat(csv.rows()).hasSize(3);
        assertThat(csv.rows().get(0).values().get(1)).isEqualTo("PAIEMENT; CB \"X\"");
        assertThat(csv.rows().get(0).values().get(2)).isEqualTo("-2,50");
        assertThat(csv.rows().get(1).values().get(1)).isEqualTo("LIGNE 1\r\nLIGNE 2");
        assertThat(csv.rows().get(0).line()).isEqualTo(2);
        assertThat(csv.rows().get(1).line()).isEqualTo(3);
        assertThat(csv.rows().get(2).line()).isEqualTo(5);
    }

    @Test
    @DisplayName("Separateur dans l'en-tete cite non pris en compte pour la detection")
    void delimiterDetectionIgnoresQuotedSeparators() {
        ParsedCsv csv = reader.read(utf8("\"Date, valeur\";Libelle;Montant\n04/03/2025;X;1,00\n"), null, null);

        assertThat(csv.delimiter()).isEqualTo(';');
        assertThat(csv.headers()).containsExactly("Date, valeur", "Libelle", "Montant");
    }

    @Test
    @DisplayName("Windows-1252 : repli 8 bits signale comme ambigu avec candidats")
    void readsWindows1252AsAmbiguous() {
        byte[] data = "Date;Libellé;Montant\n04/03/2025;CAFÉ CRÈME;-2,50\n"
                .getBytes(Charset.forName("windows-1252"));

        ParsedCsv detected = reader.read(data, null, null);
        assertThat(detected.charsetAmbiguous()).isTrue();
        assertThat(detected.charsetCandidates()).containsExactly(CsvCharset.WINDOWS_1252, CsvCharset.ISO_8859_1);

        ParsedCsv explicit = reader.read(data, CsvCharset.WINDOWS_1252, null);
        assertThat(explicit.charsetAmbiguous()).isFalse();
        assertThat(explicit.charsetCandidates()).isEmpty();
        assertThat(explicit.headers()).containsExactly("Date", "Libellé", "Montant");
        assertThat(explicit.rows().get(0).values().get(1)).isEqualTo("CAFÉ CRÈME");
    }

    @Test
    @DisplayName("ISO-8859-1 explicite")
    void readsIso88591() {
        byte[] data = "Date;Libellé;Montant\n04/03/2025;CAFÉ;-2,50\n".getBytes(StandardCharsets.ISO_8859_1);

        ParsedCsv csv = reader.read(data, CsvCharset.ISO_8859_1, null);

        assertThat(csv.headers().get(1)).isEqualTo("Libellé");
        assertThat(csv.rows().get(0).values().get(1)).isEqualTo("CAFÉ");
    }

    @Test
    @DisplayName("UTF-8 explicite sur un fichier non UTF-8 : refuse")
    void explicitUtf8OnInvalidBytesFails() {
        byte[] data = "Date;Libellé;Montant\n04/03/2025;CAFÉ;-2,50\n".getBytes(Charset.forName("windows-1252"));

        assertThatThrownBy(() -> reader.read(data, CsvCharset.UTF_8, null))
                .isInstanceOfSatisfying(ImportFileException.class, e -> assertThat(e.getCode()).isEqualTo(Code.INVALID_ENCODING));
    }

    @Test
    @DisplayName("Lignes vides, espaces inutiles et colonnes inconnues")
    void ignoresBlankLinesAndTrimsValues() {
        String text = "\n  Date ; Libelle ; Montant ; Inconnue\n\n"
                + "04/03/2025 ;  CAFE  ; -2,50 ;x\n"
                + ";;;\n"
                + "   \n"
                + "05/03/2025;PAIN;-1,00;y\n"
                + "\n";

        ParsedCsv csv = reader.read(utf8(text), null, null);

        assertThat(csv.headerLine()).isEqualTo(2);
        assertThat(csv.headers()).containsExactly("Date", "Libelle", "Montant", "Inconnue");
        assertThat(csv.rows()).hasSize(2);
        assertThat(csv.rows().get(0).values()).containsExactly("04/03/2025", "CAFE", "-2,50", "x");
        assertThat(csv.rows().get(0).line()).isEqualTo(4);
        assertThat(csv.rows().get(1).line()).isEqualTo(7);
    }

    @Test
    @DisplayName("Espaces insecables nettoyes en debut et fin de valeur")
    void trimsNoBreakSpaces() {
        ParsedCsv csv = reader.read(utf8("Date;Libelle;Montant\n04/03/2025;\u00A0CAFE\u00A0;\u202F-2,50\n"), null, null);

        assertThat(csv.rows().get(0).values()).containsExactly("04/03/2025", "CAFE", "-2,50");
    }

    @Test
    @DisplayName("Limite du nombre de lignes")
    void enforcesRowLimit() {
        CsvFileReader limited = new CsvFileReader(props(2, 50, 2000, Duration.ofSeconds(30)));
        String text = "A;B\n1;1\n2;2\n3;3\n";

        assertThatThrownBy(() -> limited.read(utf8(text), null, null))
                .isInstanceOfSatisfying(ImportFileException.class, e -> assertThat(e.getCode()).isEqualTo(Code.TOO_MANY_ROWS));
        assertThat(limited.read(utf8("A;B\n1;1\n2;2\n"), null, null).rows()).hasSize(2);
    }

    @Test
    @DisplayName("Limite du nombre de colonnes et de la taille d'un champ")
    void enforcesColumnAndFieldLimits() {
        CsvFileReader narrow = new CsvFileReader(props(100, 3, 2000, Duration.ofSeconds(30)));
        assertThatThrownBy(() -> narrow.read(utf8("A;B;C;D\n1;2;3;4\n"), null, null))
                .isInstanceOfSatisfying(ImportFileException.class, e -> assertThat(e.getCode()).isEqualTo(Code.TOO_MANY_COLUMNS));

        CsvFileReader shortFields = new CsvFileReader(props(100, 50, 10, Duration.ofSeconds(30)));
        assertThatThrownBy(() -> shortFields.read(utf8("A;B\n1;" + "x".repeat(11) + "\n"), null, null))
                .isInstanceOfSatisfying(ImportFileException.class, e -> assertThat(e.getCode()).isEqualTo(Code.FIELD_TOO_LONG));
    }

    @Test
    @DisplayName("Delai de traitement depasse")
    void enforcesTimeout() {
        CsvFileReader instant = new CsvFileReader(props(100, 50, 2000, Duration.ZERO));

        assertThatThrownBy(() -> instant.read(utf8("A;B\n1;1\n"), null, null))
                .isInstanceOfSatisfying(ImportFileException.class, e -> assertThat(e.getCode()).isEqualTo(Code.PROCESSING_TIMEOUT));
    }

    @Test
    @DisplayName("Guillemets mal formes : erreur generique sans contenu")
    void malformedQuotesAreReportedGenerically() {
        assertThatThrownBy(() -> reader.read(utf8("A;B\n\"abc\"def;2\n"), null, null))
                .isInstanceOfSatisfying(ImportFileException.class, e -> {
                    assertThat(e.getCode()).isEqualTo(Code.MALFORMED_CSV);
                    assertThat(e.getMessage()).doesNotContain("abc");
                });
    }

    @Test
    @DisplayName("Separateur explicite prioritaire sur la detection")
    void explicitDelimiterWins() {
        ParsedCsv csv = reader.read(utf8("A,B;C\n1,2;3\n"), null, ',');

        assertThat(csv.headers()).containsExactly("A", "B;C");
    }

    @Test
    @DisplayName("FileValidator : fichier vide, blanc, binaire, signature ZIP/PDF, trop gros")
    void validatesRawBytes() {
        assertThatThrownBy(() -> validator.validateBytes(new byte[0]))
                .isInstanceOfSatisfying(ImportFileException.class, e -> assertThat(e.getCode()).isEqualTo(Code.EMPTY_FILE));
        assertThatThrownBy(() -> validator.validateBytes(utf8(" \r\n\t ")))
                .isInstanceOfSatisfying(ImportFileException.class, e -> assertThat(e.getCode()).isEqualTo(Code.EMPTY_FILE));
        assertThatThrownBy(() -> validator.validateBytes(new byte[]{'P', 'K', 3, 4, 20, 0, 0, 0}))
                .isInstanceOfSatisfying(ImportFileException.class, e -> assertThat(e.getCode()).isEqualTo(Code.BINARY_FILE));
        assertThatThrownBy(() -> validator.validateBytes(utf8("%PDF-1.7 contenu")))
                .isInstanceOfSatisfying(ImportFileException.class, e -> assertThat(e.getCode()).isEqualTo(Code.BINARY_FILE));
        assertThatThrownBy(() -> validator.validateBytes(utf8("A;B\n1;\u0000\n")))
                .isInstanceOfSatisfying(ImportFileException.class, e -> assertThat(e.getCode()).isEqualTo(Code.BINARY_FILE));
        assertThatThrownBy(() -> validator.validateBytes(new byte[(int) defaults.maxFileBytes() + 1]))
                .isInstanceOfSatisfying(ImportFileException.class, e -> assertThat(e.getCode()).isEqualTo(Code.FILE_TOO_LARGE));
        validator.validateBytes(utf8("A;B\n1;2\n"));
    }

    @Test
    @DisplayName("FileValidator : structure minimale (2 colonnes, 1 ligne de donnees)")
    void validatesStructure() {
        ParsedCsv oneColumn = reader.read(utf8("Texte libre sans separateur\nautre ligne\n"), null, null);
        assertThatThrownBy(() -> validator.validateStructure(oneColumn))
                .isInstanceOfSatisfying(ImportFileException.class, e -> assertThat(e.getCode()).isEqualTo(Code.NOT_CSV));

        ParsedCsv headerOnly = reader.read(utf8("Date;Libelle;Montant\n"), null, null);
        assertThatThrownBy(() -> validator.validateStructure(headerOnly))
                .isInstanceOfSatisfying(ImportFileException.class, e -> assertThat(e.getCode()).isEqualTo(Code.NO_DATA_ROWS));

        validator.validateStructure(reader.read(utf8("Date;Libelle\n04/03/2025;X\n"), null, null));
    }

    @Test
    @DisplayName("Noms d'encodage et de separateur acceptes")
    void parsesParameterNames() {
        assertThat(CsvCharset.fromName("windows-1252")).contains(CsvCharset.WINDOWS_1252);
        assertThat(CsvCharset.fromName("ISO_8859_1")).contains(CsvCharset.ISO_8859_1);
        assertThat(CsvCharset.fromName("utf-8")).contains(CsvCharset.UTF_8);
        assertThat(CsvCharset.fromName("klingon")).isEmpty();
        assertThat(DelimiterDetector.parse(";")).isEqualTo(';');
        assertThat(DelimiterDetector.parse("COMMA")).isEqualTo(',');
        assertThat(DelimiterDetector.parse(null)).isNull();
        assertThatThrownBy(() -> DelimiterDetector.parse("|")).isInstanceOf(IllegalArgumentException.class);
        assertThat(List.of(CsvCharset.values())).hasSize(3);
    }
}
