package com.eparly.api.imports.parser;

import com.eparly.api.imports.service.ImportProperties;
import com.eparly.api.imports.validation.ImportFileException;
import com.eparly.api.imports.validation.ImportFileException.Code;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.StringReader;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

/**
 * Lecture physique du CSV (Apache Commons CSV) : encodage, separateur, champs cites,
 * retours a la ligne dans les champs, lignes vides, espaces inutiles. Applique les limites de ressources.
 */
@Component
public class CsvFileReader {

    private final ImportProperties properties;

    public CsvFileReader(ImportProperties properties) {
        this.properties = properties;
    }

    /**
     * @param explicitCharset   encodage choisi par l'utilisateur (null = detection)
     * @param explicitDelimiter separateur choisi par l'utilisateur (null = detection)
     */
    public ParsedCsv read(byte[] data, CsvCharset explicitCharset, Character explicitDelimiter) {
        CharsetDetector.Detection detection = CharsetDetector.detect(data);
        CsvCharset charset = explicitCharset != null ? explicitCharset : detection.charset();
        boolean ambiguous = explicitCharset == null && detection.ambiguous();
        List<CsvCharset> candidates = ambiguous ? detection.candidates() : List.of();

        String text = CharsetDetector.decode(data, charset);
        if (!text.isEmpty() && text.charAt(0) == '\uFEFF') {
            text = text.substring(1);
        }
        char delimiter = explicitDelimiter != null ? explicitDelimiter : DelimiterDetector.detect(text);
        int[] lineStarts = computeLineStarts(text);

        CSVFormat format = CSVFormat.DEFAULT.builder()
                .setDelimiter(delimiter)
                .setQuote('"')
                .setIgnoreEmptyLines(false)
                .setIgnoreSurroundingSpaces(false)
                .setTrim(false)
                .get();

        long deadline = System.nanoTime() + properties.processingTimeout().toNanos();
        List<String> headers = null;
        int headerLine = 0;
        List<ParsedCsv.CsvRow> rows = new ArrayList<>();
        int lastLine = 0;

        try (CSVParser parser = format.parse(new StringReader(text))) {
            Iterator<CSVRecord> iterator = parser.iterator();
            while (iterator.hasNext()) {
                if (System.nanoTime() > deadline) {
                    throw new ImportFileException(Code.PROCESSING_TIMEOUT,
                            "Le traitement du fichier a depasse le delai autorise");
                }
                CSVRecord record = iterator.next();
                int line = lineOf(lineStarts, record.getCharacterPosition());
                lastLine = line;
                List<String> values = new ArrayList<>(record.size());
                boolean blank = true;
                for (String raw : record) {
                    if (raw.length() > properties.maxFieldLength()) {
                        throw new ImportFileException(Code.FIELD_TOO_LONG,
                                "Un champ depasse la taille maximale autorisee", line);
                    }
                    String cleaned = clean(raw);
                    if (!cleaned.isEmpty()) {
                        blank = false;
                    }
                    values.add(cleaned);
                }
                if (blank) {
                    continue;
                }
                if (headers == null) {
                    if (values.size() > properties.maxColumns()) {
                        throw new ImportFileException(Code.TOO_MANY_COLUMNS,
                                "Le fichier contient trop de colonnes", line);
                    }
                    headers = values;
                    headerLine = line;
                } else {
                    if (rows.size() >= properties.maxRows()) {
                        throw new ImportFileException(Code.TOO_MANY_ROWS,
                                "Le fichier depasse le nombre maximal de lignes autorise (" + properties.maxRows() + ")", line);
                    }
                    rows.add(new ParsedCsv.CsvRow(line, List.copyOf(values)));
                }
            }
        } catch (UncheckedIOException | IOException | IllegalStateException e) {
            throw new ImportFileException(Code.MALFORMED_CSV,
                    "Le fichier CSV est mal forme (guillemets ou separateurs invalides) a proximite de la ligne " + (lastLine + 1),
                    lastLine + 1);
        }

        if (headers == null) {
            throw new ImportFileException(Code.EMPTY_FILE, "Le fichier ne contient aucune donnee");
        }
        return new ParsedCsv(charset, ambiguous, candidates, delimiter, headerLine, List.copyOf(headers), List.copyOf(rows));
    }

    /** Supprime les espaces (dont insecables et fins) en debut et fin de valeur. */
    static String clean(String value) {
        int start = 0;
        int end = value.length();
        while (start < end && isSpace(value.charAt(start))) {
            start++;
        }
        while (end > start && isSpace(value.charAt(end - 1))) {
            end--;
        }
        return value.substring(start, end);
    }

    private static boolean isSpace(char c) {
        return Character.isWhitespace(c) || c == '\u00A0' || c == '\u202F' || c == '\u2007'
                || c == '\u200B' || c == '\uFEFF';
    }

    private static int[] computeLineStarts(String text) {
        int count = 1;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '\n' || (c == '\r' && (i + 1 >= text.length() || text.charAt(i + 1) != '\n'))) {
                count++;
            }
        }
        int[] starts = new int[count];
        int index = 1;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '\n' || (c == '\r' && (i + 1 >= text.length() || text.charAt(i + 1) != '\n'))) {
                starts[index++] = i + 1;
            }
        }
        return starts;
    }

    private static int lineOf(int[] lineStarts, long position) {
        int found = Arrays.binarySearch(lineStarts, (int) Math.min(position, Integer.MAX_VALUE));
        int lineIndex = found >= 0 ? found : -found - 2;
        return Math.max(lineIndex, 0) + 1;
    }
}
