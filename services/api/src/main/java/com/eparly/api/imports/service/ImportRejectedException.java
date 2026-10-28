package com.eparly.api.imports.service;

import com.eparly.api.imports.dto.ImportIssueDto;

import java.util.List;
import java.util.UUID;

/**
 * Import refuse a cause d'erreurs bloquantes. Rien n'a ete enregistre, hormis un ImportJob FAILED.
 */
public class ImportRejectedException extends RuntimeException {

    private final UUID importJobId;
    private final List<ImportIssueDto> errors;
    private final int issueCount;
    private final boolean truncated;

    public ImportRejectedException(UUID importJobId, List<ImportIssueDto> errors, int issueCount, boolean truncated) {
        super("Le fichier contient des erreurs bloquantes : aucune transaction n'a ete importee");
        this.importJobId = importJobId;
        this.errors = errors;
        this.issueCount = issueCount;
        this.truncated = truncated;
    }

    public UUID getImportJobId() {
        return importJobId;
    }

    public List<ImportIssueDto> getErrors() {
        return errors;
    }

    public int getIssueCount() {
        return issueCount;
    }

    public boolean isTruncated() {
        return truncated;
    }
}
