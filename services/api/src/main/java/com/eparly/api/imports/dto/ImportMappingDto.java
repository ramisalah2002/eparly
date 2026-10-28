package com.eparly.api.imports.dto;

import com.eparly.api.imports.mapper.ImportMapping;
import com.eparly.api.imports.parser.AmountFormat;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Association des colonnes du CSV (par nom d'en-tete, insensible a la casse et aux accents) aux champs financiers. "
        + "Renseigner soit amountColumn, soit debitColumn ET creditColumn.")
public record ImportMappingDto(
        @Schema(description = "Colonne de la date d'operation (obligatoire)", example = "Date operation") String dateColumn,
        @Schema(description = "Colonne de la date de valeur (optionnelle)", example = "Date valeur") String valueDateColumn,
        @Schema(description = "Colonne du libelle (obligatoire)", example = "Libelle") String labelColumn,
        @Schema(description = "Colonne du montant signe", example = "Montant") String amountColumn,
        @Schema(description = "Colonne des debits (avec creditColumn)", nullable = true) String debitColumn,
        @Schema(description = "Colonne des credits (avec debitColumn)", nullable = true) String creditColumn,
        @Schema(description = "Colonne de la devise (optionnelle, doit correspondre au compte)", nullable = true) String currencyColumn,
        @Schema(description = "Colonne d'un identifiant de transaction fiable et unique (optionnelle)", nullable = true) String sourceIdColumn,
        @Schema(description = "Format de date : AUTO (preview uniquement) ou pattern de la liste blanche "
                + "(dd/MM/yyyy, dd-MM-yyyy, dd.MM.yyyy, yyyy-MM-dd, yyyy/MM/dd, MM/dd/yyyy, MM-dd-yyyy, yyyyMMdd). "
                + "Doit etre explicite pour l'import.", example = "dd/MM/yyyy") String dateFormat,
        @Schema(description = "Convention des montants : AUTO, FR (virgule decimale) ou EN (point decimal)", example = "FR") AmountFormat amountFormat
) {

    public static ImportMappingDto from(ImportMapping mapping) {
        return new ImportMappingDto(mapping.dateColumn(), mapping.valueDateColumn(), mapping.labelColumn(),
                mapping.amountColumn(), mapping.debitColumn(), mapping.creditColumn(), mapping.currencyColumn(),
                mapping.sourceIdColumn(), mapping.dateFormat(), mapping.amountFormat());
    }

    public ImportMapping toMapping() {
        return new ImportMapping(dateColumn, valueDateColumn, labelColumn, amountColumn, debitColumn, creditColumn,
                currencyColumn, sourceIdColumn, dateFormat, amountFormat);
    }
}
