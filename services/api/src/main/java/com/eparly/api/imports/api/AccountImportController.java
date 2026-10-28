package com.eparly.api.imports.api;

import com.eparly.api.auth.dto.ApiErrorResponse;
import com.eparly.api.imports.dto.ImportJobResponse;
import com.eparly.api.imports.dto.PageResponse;
import com.eparly.api.imports.service.ImportQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/v1/accounts/{accountId}/imports")
@Tag(name = "Imports", description = "Import de releves bancaires CSV : previsualisation, confirmation et historique")
public class AccountImportController {

    private final ImportQueryService queryService;

    AccountImportController(ImportQueryService queryService) {
        this.queryService = queryService;
    }

    @GetMapping
    @Operation(summary = "Lister l'historique des imports d'un compte",
            description = "Le compte doit appartenir a l'utilisateur connecte (sinon 404 neutre). Tri : createdAt decroissant.",
            security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponse(responseCode = "200", description = "Page d'imports du compte")
    @ApiResponse(responseCode = "400", description = "Pagination ou identifiant invalide",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(responseCode = "401", description = "Jeton absent, invalide ou expire",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(responseCode = "404", description = "Compte inconnu ou n'appartenant pas a l'utilisateur",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    public PageResponse<ImportJobResponse> list(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable("accountId") UUID accountId,
            @Schema(description = "Numero de page (commence a 0)", example = "0") @RequestParam(value = "page", defaultValue = "0") int page,
            @Schema(description = "Taille de page (20 par defaut, 100 maximum)", example = "20") @RequestParam(value = "size", defaultValue = "20") int size
    ) {
        return queryService.listForAccount(ImportRequestParser.userId(jwt), accountId, page, size);
    }
}
