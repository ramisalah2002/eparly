package com.eparly.api.imports.validation;

import com.eparly.api.imports.mapper.ResolvedMapping;
import com.eparly.api.imports.parser.AmountParser;
import com.eparly.api.imports.parser.DateParser;
import com.eparly.api.imports.parser.ParsedCsv;
import com.eparly.api.imports.parser.ValueParseException;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Valide chaque ligne du CSV selon le mapping et produit des lignes normalisees (dates, centimes, libelles).
 * Une ligne en erreur est exclue du resultat ; l'import atomique decide ensuite de tout refuser.
 * Les messages ne reprennent jamais les valeurs lues.
 */
@Component
public class RowValidator {

    static final int MAX_LABEL_LENGTH = 500;
    static final int MAX_SOURCE_ID_LENGTH = 128;

    /** Ligne validee, prete a etre transformee en transaction candidate. */
    public record ValidatedRow(
            int line,
            LocalDate bookingDate,
            LocalDate valueDate,
            long amountMinor,
            String currency,
            String label,
            String normalizedLabel,
            String sourceUid,
            String rawCategory,
            String rawSubcategory
    ) {
        public ValidatedRow(
                int line,
                LocalDate bookingDate,
                LocalDate valueDate,
                long amountMinor,
                String currency,
                String label,
                String normalizedLabel,
                String sourceUid
        ) {
            this(line, bookingDate, valueDate, amountMinor, currency, label, normalizedLabel, sourceUid, null, null);
        }
    }

    public record Result(List<ValidatedRow> rows, List<ImportIssue> issues, int invalidRows) {
    }

    /**
     * @param mapping         mapping resolu ; le format de date doit etre explicite (pas AUTO)
     * @param accountCurrency devise du compte (les montants sont interpretes dans cette devise)
     */
    public Result validate(ParsedCsv csv, ResolvedMapping mapping, String accountCurrency) {
        if (DateParser.AUTO.equals(mapping.dateFormat())) {
            throw new IllegalStateException("Le format de date doit etre resolu avant la validation des lignes");
        }
        List<ValidatedRow> valid = new ArrayList<>();
        List<ImportIssue> issues = new ArrayList<>();
        int invalid = 0;
        int columnCount = csv.headers().size();

        for (ParsedCsv.CsvRow row : csv.rows()) {
            int errorsBefore = errorCount(issues);
            int line = row.line();

            if (row.values().size() > columnCount) {
                boolean extraContent = false;
                for (int i = columnCount; i < row.values().size(); i++) {
                    if (!row.values().get(i).isEmpty()) {
                        extraContent = true;
                    }
                }
                if (extraContent) {
                    issues.add(ImportIssue.error(line, null, "COLUMN_COUNT_MISMATCH",
                            "La ligne contient plus de colonnes que l'en-tete (separateur ou guillemets suspects)"));
                }
            }

            LocalDate booking = parseDate(row.valueAt(mapping.dateIndex()), mapping, mapping.dateIndex(), line,
                    true, issues);
            LocalDate valueDate = mapping.valueDateIndex() >= 0
                    ? parseDate(row.valueAt(mapping.valueDateIndex()), mapping, mapping.valueDateIndex(), line,
                    false, issues)
                    : null;

            String label = row.valueAt(mapping.labelIndex());
            if (label.isEmpty()) {
                issues.add(ImportIssue.error(line, mapping.headerName(mapping.labelIndex()), "LABEL_MISSING",
                        "Libelle manquant"));
            } else if (label.length() > MAX_LABEL_LENGTH) {
                label = label.substring(0, MAX_LABEL_LENGTH).trim();
                issues.add(ImportIssue.warning(line, mapping.headerName(mapping.labelIndex()), "LABEL_TRUNCATED",
                        "Libelle tronque a " + MAX_LABEL_LENGTH + " caracteres"));
            }

            Long amount = parseAmount(row, mapping, accountCurrency, line, issues);

            String currency = accountCurrency;
            if (mapping.currencyIndex() >= 0) {
                String declared = row.valueAt(mapping.currencyIndex());
                if (!declared.isEmpty() && !declared.toUpperCase(Locale.ROOT).equals(accountCurrency)) {
                    issues.add(ImportIssue.error(line, mapping.headerName(mapping.currencyIndex()),
                            "CURRENCY_MISMATCH", "La devise ne correspond pas a celle du compte"));
                }
            }

            String sourceUid = null;
            if (mapping.sourceIdIndex() >= 0) {
                String id = row.valueAt(mapping.sourceIdIndex());
                if (id.length() > MAX_SOURCE_ID_LENGTH) {
                    issues.add(ImportIssue.error(line, mapping.headerName(mapping.sourceIdIndex()),
                            "SOURCE_ID_TOO_LONG", "Identifiant de transaction trop long"));
                } else if (!id.isEmpty()) {
                    sourceUid = id;
                }
            }

            String rawCategory = mapping.categoryIndex() >= 0 ? row.valueAt(mapping.categoryIndex()) : null;
            String rawSubcategory = mapping.subcategoryIndex() >= 0 ? row.valueAt(mapping.subcategoryIndex()) : null;

            if (errorCount(issues) == errorsBefore) {
                valid.add(new ValidatedRow(line, booking, valueDate, amount, currency, label,
                        LabelNormalizer.normalize(label), sourceUid, rawCategory, rawSubcategory));
            } else {
                invalid++;
            }
        }
        return new Result(valid, issues, invalid);
    }

    private LocalDate parseDate(String raw, ResolvedMapping mapping, int index, int line, boolean required,
                                List<ImportIssue> issues) {
        String column = mapping.headerName(index);
        if (raw.isEmpty()) {
            if (required) {
                issues.add(ImportIssue.error(line, column, "DATE_MISSING", "Date d'operation manquante"));
            }
            return null;
        }
        try {
            return DateParser.parse(raw, mapping.dateFormat());
        } catch (ValueParseException e) {
            issues.add(ImportIssue.error(line, column, e.getCode(), e.getMessage()));
            return null;
        }
    }

    private Long parseAmount(ParsedCsv.CsvRow row, ResolvedMapping mapping, String currency, int line,
                             List<ImportIssue> issues) {
        try {
            if (mapping.signedAmount()) {
                String raw = row.valueAt(mapping.amountIndex());
                if (raw.isEmpty()) {
                    issues.add(ImportIssue.error(line, mapping.headerName(mapping.amountIndex()),
                            "AMOUNT_MISSING", "Montant manquant"));
                    return null;
                }
                return checkZero(AmountParser.parse(raw, mapping.amountFormat(), currency),
                        mapping.headerName(mapping.amountIndex()), line, issues);
            }
            String debit = row.valueAt(mapping.debitIndex());
            String credit = row.valueAt(mapping.creditIndex());
            if (debit.isEmpty() && credit.isEmpty()) {
                issues.add(ImportIssue.error(line, mapping.headerName(mapping.debitIndex()),
                        "AMOUNT_MISSING", "Ni debit ni credit renseigne"));
                return null;
            }
            if (!debit.isEmpty() && !credit.isEmpty()) {
                issues.add(ImportIssue.error(line, mapping.headerName(mapping.debitIndex()),
                        "AMBIGUOUS_DEBIT_CREDIT", "Debit et credit sont tous deux renseignes"));
                return null;
            }
            if (!debit.isEmpty()) {
                long value = AmountParser.parse(debit, mapping.amountFormat(), currency);
                return checkZero(value == Long.MIN_VALUE ? value : -Math.abs(value),
                        mapping.headerName(mapping.debitIndex()), line, issues);
            }
            long value = AmountParser.parse(credit, mapping.amountFormat(), currency);
            if (value < 0) {
                issues.add(ImportIssue.error(line, mapping.headerName(mapping.creditIndex()),
                        "NEGATIVE_CREDIT", "Un credit ne peut pas etre negatif"));
                return null;
            }
            return checkZero(value, mapping.headerName(mapping.creditIndex()), line, issues);
        } catch (ValueParseException e) {
            String column = mapping.signedAmount() ? mapping.headerName(mapping.amountIndex()) : null;
            issues.add(ImportIssue.error(line, column, e.getCode(), e.getMessage()));
            return null;
        }
    }

    private Long checkZero(long value, String column, int line, List<ImportIssue> issues) {
        if (value == 0) {
            issues.add(ImportIssue.warning(line, column, "ZERO_AMOUNT", "Montant nul"));
        }
        return value;
    }

    private static int errorCount(List<ImportIssue> issues) {
        int n = 0;
        for (ImportIssue issue : issues) {
            if (issue.isError()) {
                n++;
            }
        }
        return n;
    }
}
