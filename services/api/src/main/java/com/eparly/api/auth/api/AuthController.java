package com.eparly.api.auth.api;

import com.eparly.api.auth.dto.ApiErrorResponse;
import com.eparly.api.auth.dto.EmailRequest;
import com.eparly.api.auth.dto.ForgotPasswordRequest;
import com.eparly.api.auth.dto.LoginRequest;
import com.eparly.api.auth.dto.MessageResponse;
import com.eparly.api.auth.dto.RefreshTokenRequest;
import com.eparly.api.auth.dto.RegisterRequest;
import com.eparly.api.auth.dto.ResetPasswordRequest;
import com.eparly.api.auth.dto.TokenResponse;
import com.eparly.api.auth.dto.VerifyEmailRequest;
import com.eparly.api.auth.service.AuthService;
import com.eparly.api.auth.service.ClientIpResolver;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/auth")
@Tag(name = "Authentication", description = "Création et vérification des comptes, connexion, gestion et réinitialisation des mots de passe")
public class AuthController {
    private final AuthService authService;
    private final ClientIpResolver clientIpResolver;

    public AuthController(AuthService authService, ClientIpResolver clientIpResolver) {
        this.authService = authService;
        this.clientIpResolver = clientIpResolver;
    }

    @PostMapping("/register")
    @Operation(summary = "Créer un compte utilisateur")
    @ApiResponse(responseCode = "201", description = "Compte créé, email de vérification envoyé")
    @ApiResponse(responseCode = "400", description = "Données invalides ou conditions non acceptées",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(responseCode = "409", description = "Email déjà utilisé",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(responseCode = "429", description = "Trop de requêtes d'inscription depuis cette adresse IP",
            headers = @Header(name = "Retry-After", description = "Nombre de secondes avant de pouvoir réitérer", schema = @Schema(type = "integer")),
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(responseCode = "503", description = "Service email indisponible",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    public ResponseEntity<MessageResponse> register(@Valid @RequestBody RegisterRequest request, HttpServletRequest httpRequest) {
        String clientIp = clientIpResolver.resolveClientIp(httpRequest);
        authService.register(request, clientIp);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new MessageResponse("Account created; check your email to verify it"));
    }

    @PostMapping("/verify-email")
    @Operation(summary = "Vérifier l'adresse email")
    @ApiResponse(responseCode = "200", description = "Adresse vérifiée")
    @ApiResponse(responseCode = "400", description = "Jeton invalide, expiré ou déjà utilisé",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    public MessageResponse verifyEmail(@Valid @RequestBody VerifyEmailRequest request) {
        authService.verifyEmail(request.token());
        return new MessageResponse("Email verified successfully");
    }

    @PostMapping("/resend-verification")
    @Operation(summary = "Renvoyer le message de vérification")
    @ApiResponse(responseCode = "200", description = "Réponse neutre, que le compte existe ou non")
    @ApiResponse(responseCode = "400", description = "Adresse email invalide",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(responseCode = "429", description = "Trop de demandes d'envoi",
            headers = @Header(name = "Retry-After", description = "Nombre de secondes avant de pouvoir réitérer", schema = @Schema(type = "integer")),
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    public MessageResponse resendVerification(@Valid @RequestBody EmailRequest request, HttpServletRequest httpRequest) {
        String clientIp = clientIpResolver.resolveClientIp(httpRequest);
        authService.resendVerification(request.email(), clientIp);
        return new MessageResponse("If the account can be verified, a message will be sent");
    }

    @PostMapping("/login")
    @Operation(summary = "Se connecter et recevoir un access token")
    @ApiResponse(responseCode = "200", description = "JWT de courte durée et refresh token opaque")
    @ApiResponse(responseCode = "400", description = "Format des identifiants invalide",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(responseCode = "401", description = "Identifiants invalides ou compte indisponible",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class),
                    examples = @ExampleObject(value = """
                    {"status":401,"error":"Unauthorized","message":"Invalid email or password"}
                    """)))
    @ApiResponse(responseCode = "429", description = "Trop d'échecs de connexion (blocage temporaire anti-bruteforce)",
            headers = @Header(name = "Retry-After", description = "Nombre de secondes avant de pouvoir réitérer", schema = @Schema(type = "integer")),
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    public TokenResponse login(@Valid @RequestBody LoginRequest request, HttpServletRequest httpRequest) {
        String clientIp = clientIpResolver.resolveClientIp(httpRequest);
        return authService.login(request, clientIp);
    }

    @PostMapping("/refresh")
    @Operation(summary = "Renouveler les deux jetons par rotation",
            description = """
                    Aucun Bearer access token n'est requis. Le refresh token opaque est transmis uniquement
                    dans le corps JSON. Chaque rotation consomme l'ancien jeton. Si une réponse de rotation
                    est perdue, la réutilisation de l'ancien jeton révoque toute la famille ; le client doit
                    se reconnecter. L'access token déjà émis reste valide jusqu'à son expiration.
                    """)
    @ApiResponse(responseCode = "200", description = "Nouveau couple access/refresh tokens")
    @ApiResponse(responseCode = "400", description = "Format de requête invalide",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(responseCode = "401", description = "Refresh token invalide, expiré, révoqué ou réutilisé",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(responseCode = "429", description = "Trop de requêtes de rafraîchissement",
            headers = @Header(name = "Retry-After", description = "Nombre de secondes avant de pouvoir réitérer", schema = @Schema(type = "integer")),
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    public TokenResponse refresh(@Valid @RequestBody RefreshTokenRequest request, HttpServletRequest httpRequest) {
        String clientIp = clientIpResolver.resolveClientIp(httpRequest);
        return authService.refresh(request.refreshToken(), clientIp);
    }

    @PostMapping("/logout")
    @Operation(summary = "Révoquer la famille de session",
            description = """
                    Aucun Bearer access token n'est requis. Le refresh token est transmis dans le corps JSON.
                    L'opération est idempotente et retourne toujours 204, y compris si le jeton est inconnu.
                    Le JWT d'accès déjà émis reste valide jusqu'à expiration.
                    """)
    @ApiResponse(responseCode = "204", description = "Session révoquée ou déjà absente")
    @ApiResponse(responseCode = "400", description = "Format de requête invalide",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    public ResponseEntity<Void> logout(@Valid @RequestBody RefreshTokenRequest request) {
        authService.logout(request.refreshToken());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/forgot-password")
    @Operation(summary = "Demande de réinitialisation de mot de passe",
            description = """
                    Retourne toujours une réponse neutre 200 OK, que l'adresse corresponde à un compte existant ou non.
                    Si le compte existe et est actif, un jeton cryptographique à usage unique (valable 30 minutes)
                    est généré et transmis par email via Mailpit (développement) ou SMTP (production).
                    """)
    @ApiResponse(responseCode = "200", description = "Réponse neutre confirmant la prise en compte")
    @ApiResponse(responseCode = "400", description = "Format d'adresse email invalide",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(responseCode = "429", description = "Trop de demandes de réinitialisation",
            headers = @Header(name = "Retry-After", description = "Nombre de secondes avant de pouvoir réitérer", schema = @Schema(type = "integer")),
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    public MessageResponse forgotPassword(@Valid @RequestBody ForgotPasswordRequest request, HttpServletRequest httpRequest) {
        String clientIp = clientIpResolver.resolveClientIp(httpRequest);
        authService.forgotPassword(request.email(), clientIp);
        return new MessageResponse("If the account exists and can receive emails, a reset message has been sent");
    }

    @PostMapping("/reset-password")
    @Operation(summary = "Valider la réinitialisation du mot de passe",
            description = """
                    Consomme atomiquement le jeton de réinitialisation, applique le nouveau mot de passe (haché avec Argon2id),
                    invalide tous les autres jetons de récupération du compte, révoque l'ensemble des refresh tokens de l'utilisateur
                    et invalide immédiatement tous les access tokens JWT en circulation. L'utilisateur doit ensuite s'authentifier
                    avec ses nouveaux identifiants. Un email d'alerte de confirmation est également expédié.
                    """)
    @ApiResponse(responseCode = "200", description = "Mot de passe modifié avec succès")
    @ApiResponse(responseCode = "400", description = "Jeton invalide, expiré, déjà consommé ou politique de mot de passe non respectée",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(responseCode = "429", description = "Trop de tentatives de réinitialisation",
            headers = @Header(name = "Retry-After", description = "Nombre de secondes avant de pouvoir réitérer", schema = @Schema(type = "integer")),
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    public MessageResponse resetPassword(@Valid @RequestBody ResetPasswordRequest request, HttpServletRequest httpRequest) {
        String clientIp = clientIpResolver.resolveClientIp(httpRequest);
        authService.resetPassword(request, clientIp);
        return new MessageResponse("Password has been reset successfully");
    }
}
