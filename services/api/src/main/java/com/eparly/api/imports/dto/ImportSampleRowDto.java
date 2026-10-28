package com.eparly.api.imports.dto;

import com.eparly.api.imports.service.ImportCandidate;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;

@Schema(description = "Transaction valide extraite du fichier (echantillon de previsualisation)")
public record ImportSampleRowDto(
        @Schema(example = "2") int line,
        @Schema(example = "2025-03-04") LocalDate bookingDate,
        @Schema(example = "2025-03-05", nullable = true) LocalDate valueDate,
        @Schema(description = "Montant signe en centimes", example = "-4550") long amountMinor,
        @Schema(example = "EUR") String currency,
        @Schema(example = "PAIEMENT CB EXEMPLE") String label
) {

    public static ImportSampleRowDto from(ImportCandidate candidate) {
        return new ImportSampleRowDto(candidate.line(), candidate.bookingDate(), candidate.valueDate(),
                candidate.amountMinor(), candidate.currency(), candidate.rawLabel());
    }
}
