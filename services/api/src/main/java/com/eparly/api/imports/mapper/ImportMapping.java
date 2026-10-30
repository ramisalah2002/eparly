package com.eparly.api.imports.mapper;

import com.eparly.api.imports.parser.AmountFormat;

/**
 * Association des colonnes du CSV (referencees par nom d'en-tete) aux champs financiers.
 * Soit {@code amountColumn} (montant signe), soit le couple {@code debitColumn} + {@code creditColumn}.
 */
public record ImportMapping(
        String dateColumn,
        String valueDateColumn,
        String labelColumn,
        String amountColumn,
        String debitColumn,
        String creditColumn,
        String currencyColumn,
        String sourceIdColumn,
        String categoryColumn,
        String subcategoryColumn,
        String dateFormat,
        AmountFormat amountFormat
) {

    public ImportMapping {
        dateColumn = blankToNull(dateColumn);
        valueDateColumn = blankToNull(valueDateColumn);
        labelColumn = blankToNull(labelColumn);
        amountColumn = blankToNull(amountColumn);
        debitColumn = blankToNull(debitColumn);
        creditColumn = blankToNull(creditColumn);
        currencyColumn = blankToNull(currencyColumn);
        sourceIdColumn = blankToNull(sourceIdColumn);
        categoryColumn = blankToNull(categoryColumn);
        subcategoryColumn = blankToNull(subcategoryColumn);
        dateFormat = blankToNull(dateFormat);
        amountFormat = amountFormat == null ? AmountFormat.AUTO : amountFormat;
    }

    public ImportMapping(
            String dateColumn,
            String valueDateColumn,
            String labelColumn,
            String amountColumn,
            String debitColumn,
            String creditColumn,
            String currencyColumn,
            String sourceIdColumn,
            String dateFormat,
            AmountFormat amountFormat
    ) {
        this(dateColumn, valueDateColumn, labelColumn, amountColumn, debitColumn, creditColumn,
                currencyColumn, sourceIdColumn, null, null, dateFormat, amountFormat);
    }

    public static ImportMapping empty() {
        return new ImportMapping(null, null, null, null, null, null, null, null, null, null, null, AmountFormat.AUTO);
    }

    public boolean hasSignedAmount() {
        return amountColumn != null;
    }

    public boolean hasDebitCredit() {
        return debitColumn != null && creditColumn != null;
    }

    /** Colonnes obligatoires presentes : date, libelle et (montant signe ou debit+credit). */
    public boolean isComplete() {
        return dateColumn != null && labelColumn != null && (hasSignedAmount() ^ hasDebitCredit());
    }

    public ImportMapping withDateFormat(String format) {
        return new ImportMapping(dateColumn, valueDateColumn, labelColumn, amountColumn, debitColumn, creditColumn,
                currencyColumn, sourceIdColumn, categoryColumn, subcategoryColumn, format, amountFormat);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
