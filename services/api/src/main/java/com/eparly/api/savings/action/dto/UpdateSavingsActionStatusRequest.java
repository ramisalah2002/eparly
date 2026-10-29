package com.eparly.api.savings.action.dto;

import com.eparly.api.savings.action.domain.ActionStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(description = "Requête de changement de statut d'une action d'économie")
public record UpdateSavingsActionStatusRequest(

        @NotNull(message = "status is required")
        @Schema(description = "Nouveau statut: PLANNED, COMMITTED, DECLARED, CANCELED")
        ActionStatus status,

        @Schema(description = "Note facultative sur le changement de statut")
        String notes
) {
}
