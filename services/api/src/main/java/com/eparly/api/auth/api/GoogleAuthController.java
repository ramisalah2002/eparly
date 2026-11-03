package com.eparly.api.auth.api;

import com.eparly.api.auth.dto.ApiErrorResponse;
import com.eparly.api.auth.dto.GoogleChallengeRequest;
import com.eparly.api.auth.dto.GoogleChallengeResponse;
import com.eparly.api.auth.dto.GoogleLinkRequest;
import com.eparly.api.auth.dto.GoogleLinkResponse;
import com.eparly.api.auth.dto.GoogleLoginRequest;
import com.eparly.api.auth.dto.TokenResponse;
import com.eparly.api.auth.service.ClientIpResolver;
import com.eparly.api.auth.service.GoogleAuthService;
import com.eparly.api.auth.service.JwtUserId;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/auth/google")
@Tag(name = "Authentication", description = "Création et vérification des comptes, connexion, gestion et réinitialisation des mots de passe")
public class GoogleAuthController {
    private final GoogleAuthService googleAuthService;
    private final ClientIpResolver clientIpResolver;

    public GoogleAuthController(GoogleAuthService googleAuthService, ClientIpResolver clientIpResolver) {
        this.googleAuthService = googleAuthService;
        this.clientIpResolver = clientIpResolver;
    }

    @PostMapping("/challenge")
    @Operation(summary = "Obtenir un challenge Google à usage unique",
            description = "LOGIN est public. LINK exige une session Eparly et lie le challenge à cet utilisateur.",
            security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponse(responseCode = "200", description = "Challenge créé")
    @ApiResponse(responseCode = "400", description = "Intention invalide",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(responseCode = "401", description = "LINK sans session Eparly valide",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(responseCode = "429", description = "Trop de requêtes",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(responseCode = "503", description = "Connexion Google désactivée",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    public GoogleChallengeResponse challenge(@Valid @RequestBody GoogleChallengeRequest request,
                                             @AuthenticationPrincipal Jwt jwt, HttpServletRequest httpRequest) {
        return googleAuthService.createChallenge(request.purpose(), jwt == null ? null : JwtUserId.from(jwt),
                clientIpResolver.resolveClientIp(httpRequest));
    }

    @PostMapping
    @Operation(summary = "Se connecter ou créer un compte avec un Google ID Token",
            description = "Exige un challenge LOGIN dont le nonce figure dans le jeton signé. Retourne la session Eparly habituelle. "
                    + "Le message d'erreur 409, 428 ou GOOGLE_CHALLENGE_INVALID est un code stable.")
    @ApiResponse(responseCode = "200", description = "Session Eparly émise")
    @ApiResponse(responseCode = "400", description = "Requête invalide",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(responseCode = "401", description = "Identité Google ou challenge invalide",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(responseCode = "403", description = "Compte indisponible",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(responseCode = "409", description = "GOOGLE_ACCOUNT_LINK_REQUIRED : email déjà utilisé par un compte Eparly",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(responseCode = "428", description = "GOOGLE_CONSENT_REQUIRED : conditions à accepter ; aucun compte créé",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(responseCode = "429", description = "Trop de requêtes",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(responseCode = "503", description = "Connexion Google désactivée ou clés Google inaccessibles",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    public TokenResponse login(@Valid @RequestBody GoogleLoginRequest request, HttpServletRequest httpRequest) {
        return googleAuthService.login(request, clientIpResolver.resolveClientIp(httpRequest));
    }

    @PostMapping("/link")
    @Operation(summary = "Lier un compte Google au compte Eparly connecté",
            security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponse(responseCode = "200", description = "Compte lié, ou déjà lié à ce même compte")
    @ApiResponse(responseCode = "401", description = "Session Eparly absente, identité Google ou challenge invalide",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(responseCode = "403", description = "Compte indisponible",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(responseCode = "409", description = "Identité Google liée ailleurs, ou autre compte Google déjà lié",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(responseCode = "503", description = "Connexion Google désactivée ou clés Google inaccessibles",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    public GoogleLinkResponse link(@Valid @RequestBody GoogleLinkRequest request,
                                   @AuthenticationPrincipal Jwt jwt, HttpServletRequest httpRequest) {
        return googleAuthService.link(JwtUserId.from(jwt), request, clientIpResolver.resolveClientIp(httpRequest));
    }
}
