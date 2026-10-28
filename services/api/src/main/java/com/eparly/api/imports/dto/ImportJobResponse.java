package com.eparly.api.imports.dto;

import com.eparly.api.imports.domain.ImportJob;
import com.eparly.api.imports.domain.ImportJobStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

@Schema(description = "Historique d'un import : compteurs et statut, sans aucune donnee bancaire")
public record ImportJobResponse(
        @Schema(example = "3f1c2a54-8d7e-4b0a-9a53-0c1d2e3f4a5b") UUID id,
        @Schema(example = "7a9b1c2d-3e4f-4a5b-8c6d-7e8f9a0b1c2d") UUID accountId,
        @Schema(description = "PENDING, PROCESSING, COMPLETED ou FAILED", example = "COMPLETED") ImportJobStatus status,
        @Schema(description = "Lignes de donnees non vides lues dans le fichier", example = "120") int totalRows,
        @Schema(description = "Transactions inserees", example = "118") int insertedRows,
        @Schema(description = "Lignes ignorees car deja presentes dans le compte", example = "2") int skippedRows,
        @Schema(description = "Lignes en erreur (import refuse, aucune insertion)", example = "0") int failedRows,
        @Schema(example = "2026-10-28T09:15:30Z") Instant createdAt,
        @Schema(example = "2026-10-28T09:15:31Z", nullable = true) Instant completedAt,
        @Schema(description = "Resume non sensible des erreurs (codes et nombres uniquement)", nullable = true) String errorSummary
) {

    public static ImportJobResponse from(ImportJob job) {
        return new ImportJobResponse(
                job.getId(),
                job.getAccount().getId(),
                job.getStatus(),
                job.getTotalRows(),
                job.getInsertedRows(),
                job.getSkippedRows(),
                job.getFailedRows(),
                job.getCreatedAt(),
                job.getCompletedAt(),
                job.getErrorSummary()
        );
    }
}
