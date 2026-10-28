package com.eparly.api.imports.api;

import com.eparly.api.auth.dto.ApiErrorResponse;
import com.eparly.api.imports.dto.ImportConfirmForm;
import com.eparly.api.imports.dto.ImportErrorResponse;
import com.eparly.api.imports.dto.ImportJobResponse;
import com.eparly.api.imports.dto.ImportPreviewForm;
import com.eparly.api.imports.dto.ImportPreviewResponse;
import com.eparly.api.imports.dto.ImportRejectedResponse;
import com.eparly.api.imports.dto.ImportResultResponse;
import com.eparly.api.imports.dto.PageResponse;
import com.eparly.api.imports.service.ImportQueryService;
import com.eparly.api.imports.service.ImportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@RestController
@RequestMapping("/v1/imports")
@Tag(name = "Imports", description = "Import de releves bancaires CSV : previsualisation, confirmation et historique")
public class ImportController {

    private final ImportService importService;
    private final ImportQueryService queryService;
    private final ImportRequestParser requestParser;

    ImportController(ImportService importService, ImportQueryService queryService, ImportRequestParser requestParser) {
        this.importService = importService;
        this.queryService = queryService;
        this.requestParser = requestParser;
    }

    @PostMapping(value = "/preview", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Previsualiser un import CSV (aucune ecriture en base)",
            description = "Analyse integralement le fichier (jusqu'a 10 000 lignes) et retourne le hash SHA-256, "
                    + "le mapping propose, un echantillon, les erreurs et avertissements numerotes par ligne "
                    + "et une estimation en lecture seule des lignes inserees/ignorees.",
            security = @SecurityRequirement(name = "bearerAuth"),
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(required = true,
                    content = @Content(mediaType = MediaType.MULTIPART_FORM_DATA_VALUE,
                            schema = @Schema(implementation = ImportPreviewForm.class))))
    @ApiResponse(responseCode = "200", description = "Previsualisation calculee",
            content = @Content(mediaType = "application/json",
                    schema = @Schema(implementation = ImportPreviewResponse.class),
                    examples = @ExampleObject(name = "Previsualisation d'un releve a corriger", value = """
                            {
                              "fileHash": "9f86d081884c7d659a2feaa0c55ad015a3bf4f1b2b0b822cd15d6c15b0f00a08",
                              "detectedCharset": "UTF_8",
                              "charsetAmbiguous": false,
                              "charsetCandidates": [],
                              "detectedDelimiter": ";",
                              "headers": ["Date operation", "Date valeur", "Libelle", "Montant"],
                              "suggestedMapping": {
                                "dateColumn": "Date operation", "valueDateColumn": "Date valeur",
                                "labelColumn": "Libelle", "amountColumn": "Montant",
                                "debitColumn": null, "creditColumn": null,
                                "currencyColumn": null, "sourceIdColumn": null,
                                "dateFormat": "dd/MM/yyyy", "amountFormat": "AUTO"
                              },
                              "appliedMapping": {
                                "dateColumn": "Date operation", "valueDateColumn": "Date valeur",
                                "labelColumn": "Libelle", "amountColumn": "Montant",
                                "debitColumn": null, "creditColumn": null,
                                "currencyColumn": null, "sourceIdColumn": null,
                                "dateFormat": "dd/MM/yyyy", "amountFormat": "AUTO"
                              },
                              "mappingComplete": true,
                              "totalRows": 120,
                              "wouldInsert": 117,
                              "wouldSkip": 2,
                              "blocking": true,
                              "sample": [
                                {"line": 2, "bookingDate": "2025-03-04", "valueDate": "2025-03-05",
                                 "amountMinor": -4550, "currency": "EUR", "label": "PAIEMENT CB EXEMPLE"}
                              ],
                              "errors": [
                                {"line": 14, "column": "Montant", "code": "INVALID_AMOUNT", "message": "Montant non reconnu"}
                              ],
                              "warnings": [
                                {"line": 9, "column": null, "code": "AMBIGUOUS_IDENTICAL_ROWS",
                                 "message": "2 lignes identiques (meme date, montant, devise et libelle)"}
                              ],
                              "issueCount": 2,
                              "truncated": false
                            }
                            """)))
    @ApiResponse(responseCode = "400", description = "Parametre invalide, fichier vide ou encodage invalide",
            content = @Content(schema = @Schema(implementation = ImportErrorResponse.class)))
    @ApiResponse(responseCode = "401", description = "Jeton absent, invalide ou expire",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(responseCode = "403", description = "Compte utilisateur inactif",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(responseCode = "404", description = "Compte inconnu ou n'appartenant pas a l'utilisateur",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(responseCode = "413", description = "Fichier trop volumineux (5 Mo maximum)",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(responseCode = "415", description = "Fichier non CSV ou binaire",
            content = @Content(schema = @Schema(implementation = ImportErrorResponse.class)))
    @ApiResponse(responseCode = "422", description = "Fichier inexploitable (trop de lignes ou de colonnes, CSV mal forme)",
            content = @Content(schema = @Schema(implementation = ImportErrorResponse.class)))
    public ImportPreviewResponse preview(
            @AuthenticationPrincipal Jwt jwt,
            @Parameter(hidden = true) @RequestParam("accountId") UUID accountId,
            @Parameter(hidden = true) @RequestParam("file") MultipartFile file,
            @Parameter(hidden = true) @RequestParam(value = "mapping", required = false) String mapping,
            @Parameter(hidden = true) @RequestParam(value = "charset", required = false) String charset,
            @Parameter(hidden = true) @RequestParam(value = "delimiter", required = false) String delimiter
    ) {
        return importService.preview(requestParser.toCommand(jwt, accountId, file, mapping, charset, delimiter, null));
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Confirmer un import CSV",
            description = "Le serveur recalcule le SHA-256 du fichier (409 s'il differe de celui de la previsualisation), "
                    + "reparse et revalide tout lui-meme, puis enregistre les transactions de facon atomique : "
                    + "une erreur bloquante n'enregistre aucune transaction (422 avec rapport, ImportJob FAILED trace). "
                    + "Les lignes deja presentes dans le compte sont ignorees (reimport sans doublon).",
            security = @SecurityRequirement(name = "bearerAuth"),
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(required = true,
                    content = @Content(mediaType = MediaType.MULTIPART_FORM_DATA_VALUE,
                            schema = @Schema(implementation = ImportConfirmForm.class))))
    @ApiResponse(responseCode = "201", description = "Import enregistre (compteurs insere/ignore)",
            content = @Content(mediaType = "application/json",
                    schema = @Schema(implementation = ImportResultResponse.class),
                    examples = @ExampleObject(name = "Reimport partiel", value = """
                            {
                              "importJob": {
                                "id": "3f1c2a54-8d7e-4b0a-9a53-0c1d2e3f4a5b",
                                "accountId": "7a9b1c2d-3e4f-4a5b-8c6d-7e8f9a0b1c2d",
                                "status": "COMPLETED",
                                "totalRows": 120,
                                "insertedRows": 118,
                                "skippedRows": 2,
                                "failedRows": 0,
                                "createdAt": "2026-10-28T09:15:30Z",
                                "completedAt": "2026-10-28T09:15:31Z",
                                "errorSummary": null
                              },
                              "warnings": [
                                {"line": 9, "column": null, "code": "AMBIGUOUS_IDENTICAL_ROWS",
                                 "message": "2 lignes identiques (meme date, montant, devise et libelle)"}
                              ]
                            }
                            """)))
    @ApiResponse(responseCode = "400", description = "Parametre invalide, mapping ou hash absent, ou CHARSET_REQUIRED (encodage ambigu)",
            content = @Content(schema = @Schema(implementation = ImportErrorResponse.class)))
    @ApiResponse(responseCode = "401", description = "Jeton absent, invalide ou expire",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(responseCode = "403", description = "Compte utilisateur inactif",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(responseCode = "404", description = "Compte inconnu ou n'appartenant pas a l'utilisateur",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(responseCode = "409", description = "FILE_HASH_MISMATCH : le fichier differe de la previsualisation, ou IMPORT_IN_PROGRESS",
            content = @Content(mediaType = "application/json",
                    schema = @Schema(implementation = ImportErrorResponse.class),
                    examples = @ExampleObject(name = "Fichier modifie apres la previsualisation", value = """
                            {
                              "timestamp": "2026-10-28T09:15:30Z",
                              "status": 409,
                              "error": "Conflict",
                              "message": "Le fichier ne correspond pas a celui qui a ete previsualise : relancez la previsualisation",
                              "path": "/v1/imports",
                              "code": "FILE_HASH_MISMATCH"
                            }
                            """)))
    @ApiResponse(responseCode = "413", description = "Fichier trop volumineux (5 Mo maximum)",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(responseCode = "415", description = "Fichier non CSV ou binaire",
            content = @Content(schema = @Schema(implementation = ImportErrorResponse.class)))
    @ApiResponse(responseCode = "422", description = "Erreurs bloquantes : rien n'a ete enregistre, rapport corrigeable",
            content = @Content(mediaType = "application/json",
                    schema = @Schema(implementation = ImportRejectedResponse.class),
                    examples = @ExampleObject(name = "Import refuse", value = """
                            {
                              "timestamp": "2026-10-28T09:15:30Z",
                              "status": 422,
                              "error": "Unprocessable Content",
                              "message": "Le fichier contient des erreurs bloquantes : aucune transaction n'a ete importee",
                              "path": "/v1/imports",
                              "importJobId": "3f1c2a54-8d7e-4b0a-9a53-0c1d2e3f4a5b",
                              "errors": [
                                {"line": 3, "column": "Date operation", "code": "INVALID_DATE", "message": "Date non reconnue"},
                                {"line": 14, "column": "Montant", "code": "INVALID_AMOUNT", "message": "Montant non reconnu"}
                              ],
                              "issueCount": 2,
                              "truncated": false
                            }
                            """)))
    public ImportResultResponse importFile(
            @AuthenticationPrincipal Jwt jwt,
            @Parameter(hidden = true) @RequestParam("accountId") UUID accountId,
            @Parameter(hidden = true) @RequestParam("file") MultipartFile file,
            @Parameter(hidden = true) @RequestParam(value = "mapping", required = false) String mapping,
            @Parameter(hidden = true) @RequestParam(value = "fileHash", required = false) String fileHash,
            @Parameter(hidden = true) @RequestParam(value = "charset", required = false) String charset,
            @Parameter(hidden = true) @RequestParam(value = "delimiter", required = false) String delimiter
    ) {
        return importService.importFile(requestParser.toCommand(jwt, accountId, file, mapping, charset, delimiter, fileHash));
    }

    @GetMapping
    @Operation(summary = "Lister l'historique de ses imports",
            description = "Uniquement les imports de l'utilisateur connecte, tries par createdAt decroissant.",
            security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponse(responseCode = "200", description = "Page d'imports",
            content = @Content(mediaType = "application/json",
                    examples = @ExampleObject(name = "Page d'historique", value = """
                            {
                              "content": [
                                {
                                  "id": "3f1c2a54-8d7e-4b0a-9a53-0c1d2e3f4a5b",
                                  "accountId": "7a9b1c2d-3e4f-4a5b-8c6d-7e8f9a0b1c2d",
                                  "status": "COMPLETED",
                                  "totalRows": 120, "insertedRows": 118, "skippedRows": 2, "failedRows": 0,
                                  "createdAt": "2026-10-28T09:15:30Z",
                                  "completedAt": "2026-10-28T09:15:31Z",
                                  "errorSummary": null
                                }
                              ],
                              "page": 0, "size": 20, "totalElements": 1, "totalPages": 1
                            }
                            """)))
    @ApiResponse(responseCode = "400", description = "Pagination invalide",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(responseCode = "401", description = "Jeton absent, invalide ou expire",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    public PageResponse<ImportJobResponse> list(
            @AuthenticationPrincipal Jwt jwt,
            @Schema(description = "Numero de page (commence a 0)", example = "0") @RequestParam(value = "page", defaultValue = "0") int page,
            @Schema(description = "Taille de page (20 par defaut, 100 maximum)", example = "20") @RequestParam(value = "size", defaultValue = "20") int size
    ) {
        return queryService.list(ImportRequestParser.userId(jwt), page, size);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Consulter un import",
            security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponse(responseCode = "200", description = "Detail de l'import",
            content = @Content(schema = @Schema(implementation = ImportJobResponse.class)))
    @ApiResponse(responseCode = "401", description = "Jeton absent, invalide ou expire",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(responseCode = "404", description = "Import inconnu ou appartenant a un autre utilisateur",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    public ImportJobResponse get(@AuthenticationPrincipal Jwt jwt, @PathVariable("id") UUID id) {
        return queryService.get(ImportRequestParser.userId(jwt), id);
    }
}
