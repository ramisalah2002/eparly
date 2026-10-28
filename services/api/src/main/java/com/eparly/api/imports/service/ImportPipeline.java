package com.eparly.api.imports.service;

import com.eparly.api.imports.mapper.ColumnMapper;
import com.eparly.api.imports.mapper.HeaderNormalizer;
import com.eparly.api.imports.mapper.ImportMapping;
import com.eparly.api.imports.mapper.ResolvedMapping;
import com.eparly.api.imports.parser.CsvCharset;
import com.eparly.api.imports.parser.CsvFileReader;
import com.eparly.api.imports.parser.DateParser;
import com.eparly.api.imports.parser.ParsedCsv;
import com.eparly.api.imports.validation.FileValidator;
import com.eparly.api.imports.validation.ImportIssue;
import com.eparly.api.imports.validation.RowValidator;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Pipeline sans etat partage par la previsualisation et l'import :
 * validation du fichier -> lecture -> mapping -> validation des lignes -> construction des candidats et empreintes.
 * Seule la persistance differe entre les deux parcours, ce qui garantit des resultats identiques.
 */
@Component
public class ImportPipeline {

    private final FileValidator fileValidator;
    private final CsvFileReader csvFileReader;
    private final ColumnMapper columnMapper;
    private final RowValidator rowValidator;
    private final FingerprintCalculator fingerprintCalculator;

    public ImportPipeline(
            FileValidator fileValidator,
            CsvFileReader csvFileReader,
            ColumnMapper columnMapper,
            RowValidator rowValidator,
            FingerprintCalculator fingerprintCalculator
    ) {
        this.fileValidator = fileValidator;
        this.csvFileReader = csvFileReader;
        this.columnMapper = columnMapper;
        this.rowValidator = rowValidator;
        this.fingerprintCalculator = fingerprintCalculator;
    }

    /**
     * @param currency                  devise du compte cible
     * @param charset                   encodage choisi par l'utilisateur (null = detection)
     * @param delimiter                 separateur choisi par l'utilisateur (null = detection)
     * @param requested                 mapping fourni (null = mapping propose automatiquement)
     * @param requireExplicitDateFormat vrai a l'import : le format de date ne doit jamais etre devine
     * @throws com.eparly.api.imports.validation.ImportFileException si le fichier est inutilisable dans son ensemble
     */
    public ImportAnalysis analyze(
            byte[] data,
            String currency,
            CsvCharset charset,
            Character delimiter,
            ImportMapping requested,
            boolean requireExplicitDateFormat
    ) {
        fileValidator.validateBytes(data);
        ParsedCsv csv = csvFileReader.read(data, charset, delimiter);
        fileValidator.validateStructure(csv);

        List<ImportIssue> issues = new ArrayList<>();
        if (csv.charsetAmbiguous()) {
            issues.add(ImportIssue.warning(0, null, "CHARSET_AMBIGUOUS",
                    "L'encodage du fichier est ambigu (Windows-1252 ou ISO-8859-1) : precisez le parametre charset"));
        }

        ImportMapping suggested = withDetectedDateFormat(columnMapper.suggest(csv.headers()), csv);
        ImportMapping applied = requested != null ? requested : suggested;

        ColumnMapper.Resolution resolution = columnMapper.resolve(applied, csv.headers());
        if (!resolution.valid()) {
            issues.addAll(resolution.issues());
            return new ImportAnalysis(csv, suggested, applied, false, issues, List.of(), 0);
        }

        ResolvedMapping resolved = resolution.mapping();
        if (DateParser.AUTO.equals(resolved.dateFormat())) {
            if (requireExplicitDateFormat) {
                issues.add(ImportIssue.error(0, "dateFormat", "MAPPING_DATE_FORMAT_REQUIRED",
                        "Le format de date doit etre explicite dans le mapping envoye a l'import"));
                return new ImportAnalysis(csv, suggested, applied, false, issues, List.of(), 0);
            }
            Optional<String> detected = DateParser.detect(columnValues(csv, resolved.dateIndex()));
            if (detected.isEmpty()) {
                issues.add(ImportIssue.error(0, "dateFormat", "DATE_FORMAT_UNDETECTED",
                        "Aucun format de date reconnu : precisez dateFormat dans le mapping"));
                return new ImportAnalysis(csv, suggested, applied, false, issues, List.of(), 0);
            }
            resolved = resolved.withDateFormat(detected.get());
            applied = applied.withDateFormat(detected.get());
        }

        RowValidator.Result result = rowValidator.validate(csv, resolved, currency);
        issues.addAll(result.issues());
        List<ImportCandidate> candidates = buildCandidates(result.rows(), issues, resolved.sourceIdIndex() >= 0);
        return new ImportAnalysis(csv, suggested, applied, true, issues, candidates, result.invalidRows());
    }

    private List<ImportCandidate> buildCandidates(
            List<RowValidator.ValidatedRow> rows,
            List<ImportIssue> issues,
            boolean hasSourceIdColumn
    ) {
        List<ImportCandidate> candidates = new ArrayList<>(rows.size());
        Map<String, Integer> uidFirstLine = new HashMap<>();
        Map<String, Integer> occurrences = new HashMap<>();
        Map<String, Integer> firstLineOfBase = new LinkedHashMap<>();
        int rowsWithoutSourceId = 0;

        for (RowValidator.ValidatedRow row : rows) {
            String fingerprint;
            if (row.sourceUid() != null) {
                Integer first = uidFirstLine.putIfAbsent(row.sourceUid(), row.line());
                if (first != null) {
                    issues.add(ImportIssue.error(row.line(), null, "DUPLICATE_SOURCE_ID",
                            "Identifiant de transaction deja utilise a la ligne " + first
                                    + " : un identifiant repete n'est pas fiable"));
                    continue;
                }
                fingerprint = fingerprintCalculator.forSourceUid(row.sourceUid());
            } else {
                if (hasSourceIdColumn) {
                    rowsWithoutSourceId++;
                }
                String base = fingerprintCalculator.contentBase(
                        row.bookingDate(), row.amountMinor(), row.currency(), row.normalizedLabel());
                int rank = occurrences.merge(base, 1, Integer::sum);
                firstLineOfBase.putIfAbsent(base, row.line());
                fingerprint = fingerprintCalculator.forContent(base, rank);
            }
            candidates.add(new ImportCandidate(row.line(), row.bookingDate(), row.valueDate(), row.amountMinor(),
                    row.currency(), row.label(), row.normalizedLabel(), row.sourceUid(), fingerprint));
        }

        for (Map.Entry<String, Integer> entry : occurrences.entrySet()) {
            if (entry.getValue() > 1) {
                issues.add(ImportIssue.warning(firstLineOfBase.get(entry.getKey()), null, "AMBIGUOUS_IDENTICAL_ROWS",
                        entry.getValue() + " lignes identiques (meme date, montant, devise et libelle) : traitees comme "
                                + "des operations distinctes, mais un reimport depuis un autre export pourrait les dupliquer"));
            }
        }
        if (rowsWithoutSourceId > 0) {
            issues.add(ImportIssue.warning(0, null, "SOURCE_ID_MISSING",
                    rowsWithoutSourceId + " ligne(s) sans identifiant source : deduplication sur le contenu de la ligne"));
        }
        return candidates;
    }

    private ImportMapping withDetectedDateFormat(ImportMapping mapping, ParsedCsv csv) {
        if (mapping.dateColumn() == null) {
            return mapping;
        }
        String wanted = HeaderNormalizer.normalize(mapping.dateColumn());
        int index = -1;
        for (int i = 0; i < csv.headers().size(); i++) {
            if (HeaderNormalizer.normalize(csv.headers().get(i)).equals(wanted)) {
                if (index >= 0) {
                    return mapping;
                }
                index = i;
            }
        }
        if (index < 0) {
            return mapping;
        }
        return DateParser.detect(columnValues(csv, index))
                .map(mapping::withDateFormat)
                .orElse(mapping);
    }

    private static List<String> columnValues(ParsedCsv csv, int index) {
        List<String> values = new ArrayList<>(csv.rows().size());
        for (ParsedCsv.CsvRow row : csv.rows()) {
            values.add(row.valueAt(index));
        }
        return values;
    }
}
