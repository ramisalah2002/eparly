package com.eparly.api.imports.domain;

/**
 * Statut d'une opération d'importation de relevé bancaire ou CSV.
 */
public enum ImportJobStatus {
    PENDING,
    PROCESSING,
    COMPLETED,
    FAILED
}
