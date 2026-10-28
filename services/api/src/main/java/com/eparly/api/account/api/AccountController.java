package com.eparly.api.account.api;

import com.eparly.api.account.dto.AccountResponse;
import com.eparly.api.account.dto.CreateAccountRequest;
import com.eparly.api.account.dto.UpdateAccountRequest;
import com.eparly.api.account.service.AccountService;
import com.eparly.api.auth.dto.ApiErrorResponse;
import com.eparly.api.auth.service.JwtUserId;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/v1/accounts")
@Tag(name = "Accounts", description = "Comptes financiers logiques CSV")
@SecurityRequirement(name = "bearerAuth")
public class AccountController {

    private final AccountService accountService;

    public AccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Créer un compte CSV", description = "Le propriétaire est toujours l'utilisateur du JWT.")
    @ApiResponse(responseCode = "201", description = "Compte créé", content = @Content(
            schema = @Schema(implementation = AccountResponse.class), examples = @ExampleObject(value = """
            {"id":"a12b34cd-5678-4ef0-9123-456789abcdef","name":"Compte courant",
             "sourceType":"CSV","currency":"EUR","createdAt":"2026-10-28T10:00:00Z",
             "updatedAt":"2026-10-28T10:00:00Z"}
            """)))
    @ApiResponse(responseCode = "400", description = "Nom ou devise invalide", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(responseCode = "401", description = "JWT absent ou invalide", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(responseCode = "409", description = "Nom déjà utilisé", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    public AccountResponse create(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody CreateAccountRequest request) {
        return accountService.create(JwtUserId.from(jwt), request);
    }

    @GetMapping
    @Operation(summary = "Lister ses comptes")
    @ApiResponse(responseCode = "200", description = "Comptes de l'utilisateur connecté")
    @ApiResponse(responseCode = "401", description = "JWT absent ou invalide", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    public List<AccountResponse> list(@AuthenticationPrincipal Jwt jwt) {
        return accountService.list(JwtUserId.from(jwt));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Consulter un compte", description = "Un compte privé inconnu ou étranger donne le même 404.")
    @ApiResponse(responseCode = "200", description = "Compte trouvé", content = @Content(schema = @Schema(implementation = AccountResponse.class)))
    @ApiResponse(responseCode = "401", description = "JWT absent ou invalide", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(responseCode = "404", description = "Compte inconnu ou appartenant à un autre utilisateur", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    public AccountResponse get(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return accountService.get(JwtUserId.from(jwt), id);
    }

    @PatchMapping("/{id}")
    @Operation(summary = "Renommer un compte ou modifier sa devise", description = "La devise n'est modifiable que si aucune transaction n'existe.")
    @ApiResponse(responseCode = "200", description = "Compte modifié", content = @Content(schema = @Schema(implementation = AccountResponse.class)))
    @ApiResponse(responseCode = "400", description = "Requête invalide", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(responseCode = "401", description = "JWT absent ou invalide", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(responseCode = "404", description = "Compte inconnu ou appartenant à un autre utilisateur", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(responseCode = "409", description = "Nom déjà utilisé ou devise verrouillée par des transactions", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    public AccountResponse update(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
                                  @Valid @RequestBody UpdateAccountRequest request) {
        return accountService.update(JwtUserId.from(jwt), id, request);
    }
}
