package com.eparly.api.auth.service;

import com.eparly.api.auth.config.RateLimitProperties;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class ClientIpResolver {
    private final boolean useForwardedFor;
    private final Set<String> trustedProxies;

    public ClientIpResolver(RateLimitProperties properties) {
        this.useForwardedFor = properties.useForwardedFor();
        this.trustedProxies = properties.trustedProxies() == null
                ? Set.of()
                : properties.trustedProxies().stream().map(String::trim).collect(Collectors.toSet());
    }

    public String resolveClientIp(HttpServletRequest request) {
        String remoteAddr = request.getRemoteAddr();
        if (remoteAddr == null || remoteAddr.isBlank()) {
            remoteAddr = "unknown";
        }

        if (!useForwardedFor) {
            return remoteAddr;
        }

        if (!trustedProxies.contains(remoteAddr)) {
            // Requête non issue d'un reverse proxy de confiance : ignorer tout en-tête client
            return remoteAddr;
        }

        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor == null || forwardedFor.isBlank()) {
            return remoteAddr;
        }

        // Parcours de la chaîne X-Forwarded-For de droite à gauche pour trouver le premier IP non proxy
        List<String> hops = Arrays.stream(forwardedFor.split(","))
                .map(String::trim)
                .filter(hop -> !hop.isBlank())
                .toList();

        for (int i = hops.size() - 1; i >= 0; i--) {
            String hop = hops.get(i);
            if (!trustedProxies.contains(hop)) {
                return hop;
            }
        }

        return remoteAddr;
    }
}
