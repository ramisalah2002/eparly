package com.eparly.api.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Résultat de l'opération")
public record MessageResponse(@Schema(example = "Email verified successfully") String message) {
}
