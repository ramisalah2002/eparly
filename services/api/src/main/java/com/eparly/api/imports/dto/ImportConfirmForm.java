package com.eparly.api.imports.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Description OpenAPI du formulaire multipart de POST /v1/imports (non instancie : les champs sont lus
 * directement par le controleur).
 */
@Schema(name = "ImportConfirmForm", description = "Formulaire multipart/form-data de confirmation d'import")
public record ImportConfirmForm(
        @Schema(description = "Compte cible (doit appartenir a l'utilisateur)", requiredMode = Schema.RequiredMode.REQUIRED,
                example = "7a9b1c2d-3e4f-4a5b-8c6d-7e8f9a0b1c2d") String accountId,
        @Schema(description = "Fichier CSV identique a celui de la previsualisation", type = "string", format = "binary",
                requiredMode = Schema.RequiredMode.REQUIRED) String file,
        @Schema(description = "Mapping JSON confirme sous forme de texte, dateFormat explicite (pas AUTO)",
                requiredMode = Schema.RequiredMode.REQUIRED,
                example = "{\"dateColumn\":\"Date operation\",\"valueDateColumn\":\"Date valeur\",\"labelColumn\":\"Libelle\","
                        + "\"amountColumn\":\"Montant\",\"dateFormat\":\"dd/MM/yyyy\",\"amountFormat\":\"FR\"}") String mapping,
        @Schema(description = "fileHash retourne par la previsualisation (SHA-256 hexadecimal minuscule)",
                requiredMode = Schema.RequiredMode.REQUIRED,
                example = "9f86d081884c7d659a2feaa0c55ad015a3bf4f1b2b0b822cd15d6c15b0f00a08") String fileHash,
        @Schema(description = "UTF_8, WINDOWS_1252 ou ISO_8859_1 : obligatoire si la previsualisation a signale charsetAmbiguous",
                example = "WINDOWS_1252") String charset,
        @Schema(description = "Separateur ; , ou tabulation (optionnel : detection)", example = ";") String delimiter
) {
}
