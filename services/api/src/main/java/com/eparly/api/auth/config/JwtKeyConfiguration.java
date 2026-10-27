package com.eparly.api.auth.config;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.Resource;
import org.springframework.security.converter.RsaKeyConverters;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtValidators;

import java.io.IOException;
import java.io.InputStream;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.UUID;

@Configuration
@Profile("!test")
public class JwtKeyConfiguration {

    @Bean
    JwtEncoder jwtEncoder(
            @Value("${eparly.security.jwt.public-key}") Resource publicKeyResource,
            @Value("${eparly.security.jwt.private-key}") Resource privateKeyResource
    ) throws IOException {
        RSAPublicKey publicKey;
        RSAPrivateKey privateKey;
        try (InputStream input = publicKeyResource.getInputStream()) {
            publicKey = RsaKeyConverters.x509().convert(input);
        }
        try (InputStream input = privateKeyResource.getInputStream()) {
            privateKey = RsaKeyConverters.pkcs8().convert(input);
        }
        RSAKey rsaKey = new RSAKey.Builder(publicKey)
                .privateKey(privateKey)
                .keyID(UUID.randomUUID().toString())
                .build();
        return new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(rsaKey)));
    }

    @Bean
    JwtDecoder jwtDecoder(
            @Value("${eparly.security.jwt.public-key}") Resource publicKeyResource,
            @Value("${eparly.security.jwt.issuer}") String issuer,
            @Value("${eparly.security.jwt.audience}") String audience
    ) throws IOException {
        RSAPublicKey publicKey;
        try (InputStream input = publicKeyResource.getInputStream()) {
            publicKey = RsaKeyConverters.x509().convert(input);
        }
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withPublicKey(publicKey).build();
        OAuth2TokenValidator<Jwt> audienceValidator = jwt ->
                jwt.getAudience().contains(audience)
                        ? OAuth2TokenValidatorResult.success()
                        : OAuth2TokenValidatorResult.failure(
                                new org.springframework.security.oauth2.core.OAuth2Error(
                                        "invalid_token", "Required audience is missing", null
                                )
                        );
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer(issuer),
                audienceValidator
        ));
        return decoder;
    }
}
