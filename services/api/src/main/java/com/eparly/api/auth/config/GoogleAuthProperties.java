package com.eparly.api.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;
import java.util.List;

/**
 * @param allowedClientIds identifiants OAuth Google acceptes (Web, Android, iOS) ; ce ne sont pas des secrets.
 *                         Liste vide : la connexion Google reste indisponible, jamais permissive.
 */
@ConfigurationProperties(prefix = "eparly.auth.google")
public record GoogleAuthProperties(
        @DefaultValue("false") boolean enabled,
        @DefaultValue("") List<String> allowedClientIds,
        @DefaultValue("https://www.googleapis.com/oauth2/v3/certs") String jwkSetUri,
        @DefaultValue("PT3S") Duration connectTimeout,
        @DefaultValue("PT5S") Duration readTimeout,
        @DefaultValue("PT30S") Duration jwkRefreshMinInterval,
        @DefaultValue("PT5M") Duration challengeTtl
) {
    public List<String> effectiveClientIds() {
        return allowedClientIds.stream().map(String::trim).filter(id -> !id.isEmpty()).toList();
    }

    public boolean usable() {
        return enabled && !effectiveClientIds().isEmpty();
    }
}
