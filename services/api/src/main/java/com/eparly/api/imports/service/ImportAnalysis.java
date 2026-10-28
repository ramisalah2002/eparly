package com.eparly.api.imports.service;

import com.eparly.api.imports.mapper.ImportMapping;
import com.eparly.api.imports.parser.ParsedCsv;
import com.eparly.api.imports.validation.ImportIssue;

import java.util.List;

/**
 * Resultat complet de l'analyse d'un fichier, identique pour la previsualisation et l'import.
 */
public record ImportAnalysis(
        ParsedCsv csv,
        ImportMapping suggestedMapping,
        ImportMapping appliedMapping,
        boolean mappingValid,
        List<ImportIssue> issues,
        List<ImportCandidate> candidates,
        int invalidRows
) {

    public boolean blocking() {
        return issues.stream().anyMatch(ImportIssue::isError);
    }

    public List<ImportIssue> errors() {
        return issues.stream().filter(ImportIssue::isError).toList();
    }

    public List<ImportIssue> warnings() {
        return issues.stream().filter(issue -> !issue.isError()).toList();
    }

    public int totalRows() {
        return csv.rows().size();
    }
}
