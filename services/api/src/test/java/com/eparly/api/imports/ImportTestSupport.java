package com.eparly.api.imports;

import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Utilitaires communs aux tests d'integration des imports (JWT signe avec la cle de test, CSV fictifs).
 */
final class ImportTestSupport {

    private ImportTestSupport() {
    }

    static String bearer(JwtEncoder encoder, UUID userId) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("eparly-test")
                .subject(userId.toString())
                .audience(List.of("eparly-test-client"))
                .issuedAt(now)
                .expiresAt(now.plusSeconds(600))
                .build();
        String token = encoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(SignatureAlgorithm.RS256).type("JWT").build(), claims
        )).getTokenValue();
        return "Bearer " + token;
    }

    static byte[] utf8(String text) {
        return text.getBytes(StandardCharsets.UTF_8);
    }

    /** Releve fictif au format francais (UTF-8, point-virgule, dates dd/MM/yyyy, montants a virgule). */
    static String statement(String... lines) {
        StringBuilder csv = new StringBuilder("Date operation;Date valeur;Libell\u00e9;Montant\n");
        for (String line : lines) {
            csv.append(line).append('\n');
        }
        return csv.toString();
    }

    static final String MAPPING_FR = "{\"dateColumn\":\"Date operation\",\"valueDateColumn\":\"Date valeur\","
            + "\"labelColumn\":\"Libell\u00e9\",\"amountColumn\":\"Montant\",\"dateFormat\":\"dd/MM/yyyy\","
            + "\"amountFormat\":\"FR\"}";
}
