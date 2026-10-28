package com.eparly.api.imports.mapper;

import com.eparly.api.imports.parser.AmountFormat;

import java.util.List;

/**
 * Mapping resolu en indices de colonnes (-1 = colonne absente).
 */
public record ResolvedMapping(
        int dateIndex,
        int valueDateIndex,
        int labelIndex,
        int amountIndex,
        int debitIndex,
        int creditIndex,
        int currencyIndex,
        int sourceIdIndex,
        String dateFormat,
        AmountFormat amountFormat,
        List<String> headers
) {

    public ResolvedMapping withDateFormat(String format) {
        return new ResolvedMapping(dateIndex, valueDateIndex, labelIndex, amountIndex, debitIndex, creditIndex,
                currencyIndex, sourceIdIndex, format, amountFormat, headers);
    }

    public boolean signedAmount() {
        return amountIndex >= 0;
    }

    public String headerName(int index) {
        return index >= 0 && index < headers.size() ? headers.get(index) : null;
    }
}
