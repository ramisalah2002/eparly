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
            if (result.statusCode() == 404) return new CachedRules(List.of(), List.of(), true,
                    System.currentTimeMillis() + 3_600_000);
            if (result.statusCode() < 200 || result.statusCode() >= 300) {
                return new CachedRules(List.of(), List.of(), false, System.currentTimeMillis() + 60_000);
            }
            return new CachedRules(rules(result.body(), "disallow"), rules(result.body(), "allow"), true, System.currentTimeMillis() + 3_600_000);
        });
        String target = uri.getRawPath() + (uri.getRawQuery() == null ? "" : "?" + uri.getRawQuery());
        if (!rules.fetchAllowed) return false;
        int disallowed = longestMatch(rules.disallowedPaths, target);
        return disallowed < 0 || longestMatch(rules.allowedPaths, target) >= disallowed;
    }

    /** Prend en charge les jokers usuels * et $ de robots.txt ; le reste est une comparaison de prefixe. */
    static boolean matches(String rule, String target) {
        boolean anchored = rule.endsWith("$");
        String body = anchored ? rule.substring(0, rule.length() - 1) : rule;
        if (!body.contains("*")) {
            return anchored ? target.equals(body) : target.startsWith(body);
        }
        StringBuilder regex = new StringBuilder();
        String[] literals = body.split("\\*", -1);
        for (int index = 0; index < literals.length; index++) {
            if (index > 0) regex.append(".*");
            regex.append(java.util.regex.Pattern.quote(literals[index]));
        }
        if (!anchored) regex.append(".*");
        return target.matches("(?s)" + regex);
    }

    /** RFC 9309 : la regle la plus longue l'emporte, et Allow l'emporte a egalite. Retourne -1 sans regle. */
    private static int longestMatch(List<String> rules, String target) {
        return rules.stream().filter(rule -> matches(rule, target)).mapToInt(String::length).max().orElse(-1);
    }

    private static List<String> rules(String robots, String directive) {
        List<String> disallowed = new ArrayList<>();
        boolean applies = false;
        boolean sawRelevantAgent = false;
        boolean sawRule = false;
        for (String line : robots.split("\\R")) {
            String content = line.split("#", 2)[0].trim();
            int separator = content.indexOf(':');
            if (separator <= 0) continue;
            String key = content.substring(0, separator).trim();
            String value = content.substring(separator + 1).trim();
            if (key.equalsIgnoreCase("user-agent")) {
                if (sawRelevantAgent && sawRule) break;
                applies = value.equals("*") || value.toLowerCase(java.util.Locale.ROOT).contains("eparly");
                sawRelevantAgent |= applies;
                continue;
            }
            if (applies && (key.equalsIgnoreCase("allow") || key.equalsIgnoreCase("disallow"))) sawRule = true;
            if (applies && key.equalsIgnoreCase(directive) && !value.isBlank()) disallowed.add(value);
        }
        return List.copyOf(disallowed);
    }

    private record CachedRules(List<String> disallowedPaths, List<String> allowedPaths, boolean fetchAllowed, long expiresAtMillis) { }
}
