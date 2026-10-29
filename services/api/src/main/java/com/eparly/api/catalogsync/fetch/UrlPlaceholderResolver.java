package com.eparly.api.catalogsync.fetch;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Resolves placeholders in URLs with values from environment variables.
 * 
 * This ensures that sensitive credentials (like API keys) are never stored in the database
 * or logged. Instead, URLs can contain placeholders like {awin_api_key} which are replaced
 * at runtime with values from environment variables.
 * 
 * Placeholder convention:
 * - Placeholders use lowercase with underscores: {provider_api_key}
 * - They are converted to uppercase for environment variable lookup: PROVIDER_API_KEY
 * - Example: {awin_api_key} -> AWIN_API_KEY environment variable
 * 
 * Security considerations:
 * - Placeholder values (credentials) are never logged or included in error messages
 * - Missing environment variables cause an exception with a generic message (no credential leak)
 * - The original URL with placeholders is masked in all outputs using {REDACTED}
 */
public final class UrlPlaceholderResolver {
    
    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("\\{([a-zA-Z0-9_]+)\\}");
    
    private UrlPlaceholderResolver() {
        // Utility class
    }

    /**
     * Resolves all placeholders in the given URL.
     * 
     * Placeholders are in the format {placeholderName}. The placeholder name is converted
     * to uppercase and used to look up an environment variable.
     * 
     * @param url The URL potentially containing placeholders
     * @return The URL with all placeholders replaced by their corresponding environment variable values
     * @throws IllegalStateException if a required environment variable is not set
     */
    public static String resolve(String url) {
        if (url == null || url.isEmpty()) {
            return url;
        }
        
        Matcher matcher = PLACEHOLDER_PATTERN.matcher(url);
        StringBuffer result = new StringBuffer();
        boolean hasPlaceholder = false;
        
        while (matcher.find()) {
            hasPlaceholder = true;
            String placeholder = matcher.group(1);
            String envVarName = placeholder.toUpperCase();
            String envValue = System.getenv(envVarName);
            
            if (envValue == null || envValue.isBlank()) {
                throw new IllegalStateException(
                        "Environment variable " + envVarName + " is required for placeholder " + placeholder + " but is not set");
            }
            
            // Escape special regex characters in the replacement
            matcher.appendReplacement(result, Matcher.quoteReplacement(envValue));
        }
        matcher.appendTail(result);
        
        return hasPlaceholder ? result.toString() : url;
    }

    /**
     * Checks if the URL contains any placeholders.
     * 
     * @param url The URL to check
     * @return true if the URL contains placeholders, false otherwise
     */
    public static boolean hasPlaceholders(String url) {
        if (url == null || url.isEmpty()) {
            return false;
        }
        return PLACEHOLDER_PATTERN.matcher(url).find();
    }

    /**
     * Masks all placeholders in the URL for safe logging.
     * 
     * Placeholders are replaced with a generic marker to prevent credential leakage
     * in logs, error messages, or any other output.
     * 
     * @param url The URL potentially containing placeholders
     * @return The URL with placeholders masked
     */
    public static String maskForLogging(String url) {
        if (url == null || url.isEmpty()) {
            return url;
        }
        return PLACEHOLDER_PATTERN.matcher(url).replaceAll("{REDACTED}");
    }

    /**
     * Validates that a URL does not contain any placeholders.
     * 
     * This is useful for ensuring that URLs stored in the database or returned
     * to clients do not contain sensitive placeholder patterns.
     * 
     * @param url The URL to validate
     * @throws IllegalArgumentException if the URL contains placeholders
     */
    public static void validateNoPlaceholders(String url) {
        if (hasPlaceholders(url)) {
            throw new IllegalArgumentException(
                    "URL must not contain placeholders: " + maskForLogging(url));
        }
    }
}
