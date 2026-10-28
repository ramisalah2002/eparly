package com.eparly.api.imports.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Description OpenAPI du formulaire multipart de POST /v1/imports/preview (non instancie : les champs sont lus
 * directement par le controleur).
 */
@Schema(name = "ImportPreviewForm", description = "Formulaire multipart/form-data de previsualisation")
public record ImportPreviewForm(
        @Schema(description = "Compte cible (doit appartenir a l'utilisateur)", requiredMode = Schema.RequiredMode.REQUIRED,
                example = "7a9b1c2d-3e4f-4a5b-8c6d-7e8f9a0b1c2d") String accountId,
        @Schema(description = "Fichier CSV (5 Mo maximum)", type = "string", format = "binary",
                requiredMode = Schema.RequiredMode.REQUIRED) String file,
        @Schema(description = "Mapping JSON sous forme de texte (optionnel : sinon mapping propose automatiquement)",
                example = "{\"dateColumn\":\"Date operation\",\"valueDateColumn\":\"Date valeur\",\"labelColumn\":\"Libelle\","
                        + "\"amountColumn\":\"Montant\",\"dateFormat\":\"dd/MM/yyyy\",\"amountFormat\":\"FR\"}") String mapping,
        @Schema(description = "UTF_8, WINDOWS_1252 ou ISO_8859_1 (optionnel : detection)", example = "WINDOWS_1252") String charset,
        @Schema(description = "Separateur ; , ou tabulation (optionnel : detection)", example = ";") String delimiter
) {
}
