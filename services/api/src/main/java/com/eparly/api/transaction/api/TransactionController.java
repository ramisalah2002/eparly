package com.eparly.api.transaction.api;

import com.eparly.api.auth.dto.ApiErrorResponse;
import com.eparly.api.auth.service.JwtUserId;
import com.eparly.api.category.dto.AssignCategoryRequest;
import com.eparly.api.category.service.CategoryTransactionService;
import com.eparly.api.imports.dto.PageResponse;
import com.eparly.api.transaction.domain.TransactionType;
import com.eparly.api.transaction.dto.TransactionResponse;
import com.eparly.api.transaction.service.TransactionQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.UUID;

@RestController
@RequestMapping("/v1/transactions")
@Tag(name = "Transactions", description = "Consultation paginée des transactions importées")
@SecurityRequirement(name = "bearerAuth")
public class TransactionController {

    private final TransactionQueryService transactionQueryService;
    private final CategoryTransactionService categoryTransactionService;

    public TransactionController(
            TransactionQueryService transactionQueryService,
            CategoryTransactionService categoryTransactionService
    ) {
        this.transactionQueryService = transactionQueryService;
        this.categoryTransactionService = categoryTransactionService;
    }

    @GetMapping
    @Operation(summary = "Lister ses transactions", description = "Les filtres se combinent. Tri fixe : bookingDate décroissante puis id décroissant.")
    @ApiResponse(responseCode = "200", description = "Page de transactions", content = @Content(
            schema = @Schema(implementation = PageResponse.class), examples = @ExampleObject(value = """
            {"content":[{"id":"b12b34cd-5678-4ef0-9123-456789abcdef",
             "accountId":"a12b34cd-5678-4ef0-9123-456789abcdef","bookingDate":"2026-03-04",
             "valueDate":"2026-03-05","amountMinor":-4550,"currency":"EUR",
             "rawLabel":"PAIEMENT CB EXEMPLE","normalizedLabel":"PAIEMENT CB EXEMPLE",
             "transactionType":"EXPENSE","categoryId":null,"customCategoryFlag":false}],
             "page":0,"size":20,"totalElements":1,"totalPages":1}
            """)))
    @ApiResponse(responseCode = "400", description = "Pagination, date ou type invalide", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(responseCode = "401", description = "JWT absent ou invalide", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(responseCode = "404", description = "Compte inconnu ou appartenant à un autre utilisateur", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    public PageResponse<TransactionResponse> list(
            @AuthenticationPrincipal Jwt jwt,
            @Parameter(description = "Page à partir de 0", example = "0") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Taille par défaut 20, plafond 100", example = "20") @RequestParam(defaultValue = "20") int size,
            @Parameter(description = "Compte appartenant à l'utilisateur connecté") @RequestParam(required = false) UUID accountId,
            @Parameter(description = "Date comptable minimale incluse (ISO-8601)", example = "2026-01-01") @RequestParam(required = false) LocalDate startDate,
            @Parameter(description = "Date comptable maximale incluse (ISO-8601)", example = "2026-12-31") @RequestParam(required = false) LocalDate endDate,
            @Parameter(description = "Type de transaction") @RequestParam(required = false) TransactionType transactionType
    ) {
        return transactionQueryService.list(JwtUserId.from(jwt), page, size, accountId,
                startDate, endDate, transactionType);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Consulter une transaction", description = "Une transaction privée inconnue ou étrangère donne le même 404.")
    @ApiResponse(responseCode = "200", description = "Transaction trouvée", content = @Content(schema = @Schema(implementation = TransactionResponse.class)))
    @ApiResponse(responseCode = "401", description = "JWT absent ou invalide", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(responseCode = "404", description = "Transaction inconnue ou appartenant à un autre utilisateur", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    public TransactionResponse get(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return transactionQueryService.get(JwtUserId.from(jwt), id);
    }

    @PatchMapping(path = "/{id}/category", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Modifier la catégorie d'une transaction",
            description = "Permet à l'utilisateur de modifier manuellement la catégorie d'une transaction qui lui appartient. "
                    + "Positionne customCategoryFlag=true pour protéger ce choix des futurs traitements automatiques. "
                    + "Envoyer {\"categoryId\": null} pour supprimer la catégorie.")
    @ApiResponse(responseCode = "200", description = "Transaction mise à jour",
            content = @Content(schema = @Schema(implementation = TransactionResponse.class)))
    @ApiResponse(responseCode = "400", description = "Type de catégorie incompatible avec le type de transaction",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(responseCode = "401", description = "JWT absent ou invalide",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(responseCode = "403", description = "Catégorie privée d'un autre utilisateur",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(responseCode = "404", description = "Transaction inconnue ou appartenant à un autre utilisateur",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    public TransactionResponse assignCategory(
            @AuthenticationPrincipal Jwt jwt,
            @Parameter(description = "ID de la transaction") @PathVariable UUID id,
            @RequestBody AssignCategoryRequest request
    ) {
        return categoryTransactionService.assignCategory(JwtUserId.from(jwt), id, request.categoryId());
    }
}
