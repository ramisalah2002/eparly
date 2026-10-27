package com.eparly.api.auth.service;

import com.eparly.api.auth.config.JwtProperties;
import com.eparly.api.user.domain.User;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

@Service
public class AccessTokenService {
    private final JwtEncoder jwtEncoder;
    private final JwtProperties jwtProperties;
    private final Clock clock;

    public AccessTokenService(JwtEncoder jwtEncoder, JwtProperties jwtProperties) {
        this.jwtEncoder = jwtEncoder;
        this.jwtProperties = jwtProperties;
        this.clock = Clock.systemUTC();
    }

    public String create(User user) {
        Instant issuedAt = clock.instant();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(jwtProperties.issuer())
                .subject(user.getId().toString())
                .audience(List.of(jwtProperties.audience()))
                .issuedAt(issuedAt)
                .expiresAt(issuedAt.plus(jwtProperties.accessTokenTtl()))
                .claim("ver", user.getTokenVersion())
                .build();
        return jwtEncoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(SignatureAlgorithm.RS256).type("JWT").build(), claims
        )).getTokenValue();
    }

    public long expiresInSeconds() {
        return jwtProperties.accessTokenTtl().toSeconds();
    }
}
