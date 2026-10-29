package com.eparly.api.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

@Schema(description = "Validation préalable du code OTP de réinitialisation")
public record VerifyResetCodeRequest(
        @Schema(example = "123456", requiredMode = Schema.RequiredMode.REQUIRED, description = "Code à 6 chiffres")
        @NotBlank @Pattern(regexp = "^[0-9]{6}$", message = "Code must be exactly 6 digits") String code
) {
}
