package com.eparly.api.imports.parser;

import java.util.List;

/**
 * Resultat de la lecture physique du CSV : en-tetes et lignes de donnees non vides (valeurs nettoyees).
 */
public record ParsedCsv(
        CsvCharset charset,
        boolean charsetAmbiguous,
        List<CsvCharset> charsetCandidates,
        char delimiter,
        int headerLine,
        List<String> headers,
        List<CsvRow> rows
) {

    /** Ligne de donnees ; {@code line} est le numero de ligne physique (1 = premiere ligne du fichier). */
    public record CsvRow(int line, List<String> values) {

        public String valueAt(int index) {
            if (index < 0 || index >= values.size()) {
                return "";
            }
            return values.get(index);
        }
    }
}
