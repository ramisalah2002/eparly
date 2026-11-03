package com.eparly.api.auth.service;

import com.eparly.api.auth.config.GoogleAuthProperties;
import com.eparly.api.auth.exception.ApiException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.jwk.source.JWKSourceBuilder;
import com.nimbusds.jose.proc.JWSVerificationKeySelector;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jose.util.DefaultResourceRetriever;
import com.nimbusds.jwt.proc.DefaultJWTProcessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.stereotype.Component;

import java.net.MalformedURLException;
import java.net.URI;
import java.util.List;
import java.util.Set;

/**
 * Verifie cryptographiquement un Google ID Token : RS256 seul, cles publiques Google mises en cache
 * (rafraichies en cas de rotation, avec delai minimal entre deux telechargements), puis issuer, audience,
 * expiration, sub et email verifie. Le jeton n'est jamais journalise ni conserve.
 */
@Component
public class GoogleIdTokenVerifier {
    public static final String INVALID_IDENTITY = "Invalid Google identity";
    private static final Logger LOGGER = LoggerFactory.getLogger(GoogleIdTokenVerifier.class);
    private static final Set<String> ISSUERS = Set.of("https://accounts.google.com", "accounts.google.com");
    private static final int MAX_JWKS_BYTES = 64 * 1024;

    public record GoogleIdentity(String subject, String email, String name, String nonce) {
    }

    private final GoogleAuthProperties properties;
    private final NimbusJwtDecoder decoder;

    public GoogleIdTokenVerifier(GoogleAuthProperties properties) {
        this.properties = properties;
        this.decoder = properties.usable() ? buildDecoder(properties) : null;
    }

    public void requireAvailable() {
        if (decoder == null) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "Google sign-in is not available");
        }
    }

    public GoogleIdentity verify(String idToken) {
        requireAvailable();
        Jwt jwt;
        try {
            jwt = decoder.decode(idToken);
        } catch (BadJwtException exception) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, INVALID_IDENTITY);
        } catch (JwtException exception) {
            // Cles Google inaccessibles : ce n'est pas un jeton invalide. Le message peut citer le jeton : non journalise.
            LOGGER.warn("Google signing keys could not be retrieved: {}", exception.getClass().getSimpleName());
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "Google sign-in is temporarily unavailable");
        }
        return new GoogleIdentity(jwt.getSubject(), jwt.getClaimAsString("email"), jwt.getClaimAsString("name"),
                jwt.getClaimAsString("nonce"));
    }

    private static NimbusJwtDecoder buildDecoder(GoogleAuthProperties properties) {
        try {
            DefaultResourceRetriever retriever = new DefaultResourceRetriever(
                    Math.toIntExact(properties.connectTimeout().toMillis()),
                    Math.toIntExact(properties.readTimeout().toMillis()), MAX_JWKS_BYTES);
            JWKSource<SecurityContext> keys = JWKSourceBuilder
                    .<SecurityContext>create(URI.create(properties.jwkSetUri()).toURL(), retriever)
                    .rateLimited(properties.jwkRefreshMinInterval().toMillis())
                    .build();
            DefaultJWTProcessor<SecurityContext> processor = new DefaultJWTProcessor<>();
            processor.setJWSKeySelector(new JWSVerificationKeySelector<>(JWSAlgorithm.RS256, keys));
            NimbusJwtDecoder decoder = new NimbusJwtDecoder(processor);
            decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                    new JwtTimestampValidator(), claims(properties.effectiveClientIds())));
            return decoder;
        } catch (MalformedURLException exception) {
            throw new IllegalStateException("Google JWK set URI is invalid", exception);
        }
    }

    private static OAuth2TokenValidator<Jwt> claims(List<String> clientIds) {
        OAuth2TokenValidatorResult failure = OAuth2TokenValidatorResult.failure(
                new OAuth2Error("invalid_token", INVALID_IDENTITY, null));
        return jwt -> {
            String azp = jwt.getClaimAsString("azp");
            boolean valid = jwt.getExpiresAt() != null && jwt.getIssuedAt() != null
                    // En texte : Google emet aussi "accounts.google.com", qui n'est pas une URL.
                    && jwt.getClaimAsString("iss") != null && ISSUERS.contains(jwt.getClaimAsString("iss"))
                    && jwt.getAudience() != null && jwt.getAudience().stream().anyMatch(clientIds::contains)
                    && (azp == null || clientIds.contains(azp))
                    && jwt.getSubject() != null && !jwt.getSubject().isBlank() && jwt.getSubject().length() <= 255
                    && jwt.getClaimAsString("email") != null && !jwt.getClaimAsString("email").isBlank()
                    && jwt.getClaimAsString("email").length() <= 255
                    && "true".equals(jwt.getClaimAsString("email_verified"));
            return valid ? OAuth2TokenValidatorResult.success() : failure;
        };
    }
}
