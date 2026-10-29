package com.eparly.api.catalogsync.fetch;

import com.eparly.api.catalogsync.domain.CatalogSyncSource;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class RobotsPolicyService {
    private final OfficialSourceHttpClient httpClient;
    private final ConcurrentHashMap<String, CachedRules> cache = new ConcurrentHashMap<>();

    public RobotsPolicyService(OfficialSourceHttpClient httpClient) {
        this.httpClient = httpClient;
    }

    public void beginRun() {
        cache.clear();
    }

    public boolean isAllowed(CatalogSyncSource source, String url) {
        URI uri = URI.create(url);
        String origin = uri.getScheme() + "://" + uri.getRawAuthority();
        CachedRules rules = cache.compute(origin, (key, previous) -> {
            if (previous != null && previous.expiresAtMillis > System.currentTimeMillis()) return previous;
            String robotsUrl = origin + "/robots.txt";
            FetchResult result = httpClient.fetch(source, robotsUrl, null, null);
            if (result.statusCode() == 404) return new CachedRules(List.of(), true,
                    System.currentTimeMillis() + 3_600_000);
            if (result.statusCode() < 200 || result.statusCode() >= 300) {
                return new CachedRules(List.of(), false, System.currentTimeMillis() + 60_000);
            }
            return new CachedRules(disallowedPaths(result.body()), true, System.currentTimeMillis() + 3_600_000);
        });
        return rules.fetchAllowed && rules.disallowedPaths.stream()
                .noneMatch(disallowed -> uri.getRawPath().startsWith(disallowed));
    }

    private static List<String> disallowedPaths(String robots) {
        List<String> disallowed = new ArrayList<>();
        boolean applies = false;
        boolean sawRelevantAgent = false;
        for (String line : robots.split("\\R")) {
            String content = line.split("#", 2)[0].trim();
            int separator = content.indexOf(':');
            if (separator <= 0) continue;
            String key = content.substring(0, separator).trim();
            String value = content.substring(separator + 1).trim();
            if (key.equalsIgnoreCase("user-agent")) {
                if (sawRelevantAgent && !disallowed.isEmpty()) break;
                applies = value.equals("*") || value.toLowerCase(java.util.Locale.ROOT).contains("eparly");
                sawRelevantAgent |= applies;
                continue;
            }
            if (applies && key.equalsIgnoreCase("disallow") && !value.isBlank()) disallowed.add(value);
        }
        return List.copyOf(disallowed);
    }

    private record CachedRules(List<String> disallowedPaths, boolean fetchAllowed, long expiresAtMillis) { }
}
