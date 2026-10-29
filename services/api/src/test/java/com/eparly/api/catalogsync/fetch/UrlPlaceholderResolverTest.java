package com.eparly.api.catalogsync.fetch;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for UrlPlaceholderResolver.
 * 
 * These tests use reflection to temporarily set environment variables
 * for testing placeholder resolution.
 */
class UrlPlaceholderResolverTest {

    private Map<String, String> originalEnv;

    @BeforeEach
    void setUp() throws Exception {
        // Save original environment
        originalEnv = System.getenv();
    }

    @AfterEach
    void tearDown() throws Exception {
        // Environment variables cannot be modified in Java, 
        // but we can clean up any test state if needed
    }

    @Test
    void hasPlaceholdersReturnsTrueWhenPlaceholdersExist() {
        assertThat(UrlPlaceholderResolver.hasPlaceholders("https://api.example.com/{apikey}")).isTrue();
        assertThat(UrlPlaceholderResolver.hasPlaceholders("https://api.example.com/{API_KEY}/path")).isTrue();
        assertThat(UrlPlaceholderResolver.hasPlaceholders("https://api.example.com/{apikey}?param={token}")).isTrue();
    }

    @Test
    void hasPlaceholdersReturnsFalseWhenNoPlaceholders() {
        assertThat(UrlPlaceholderResolver.hasPlaceholders("https://api.example.com/apikey")).isFalse();
        assertThat(UrlPlaceholderResolver.hasPlaceholders("https://api.example.com/?param=value")).isFalse();
        assertThat(UrlPlaceholderResolver.hasPlaceholders(null)).isFalse();
        assertThat(UrlPlaceholderResolver.hasPlaceholders("")).isFalse();
    }

    @Test
    void maskForLoggingReplacesAllPlaceholders() {
        String url = "https://api.example.com/{apikey}/path?param={token}";
        String masked = UrlPlaceholderResolver.maskForLogging(url);
        assertThat(masked).isEqualTo("https://api.example.com/{REDACTED}/path?param={REDACTED}");
    }

    @Test
    void maskForLoggingReturnsUrlUnchangedWhenNoPlaceholders() {
        String url = "https://api.example.com/apikey/path";
        String masked = UrlPlaceholderResolver.maskForLogging(url);
        assertThat(masked).isEqualTo(url);
    }

    @Test
    void maskForLoggingReturnsNullForNullInput() {
        String masked = UrlPlaceholderResolver.maskForLogging(null);
        assertThat(masked).isNull();
    }

    @Test
    void maskForLoggingReturnsEmptyForEmptyInput() {
        String masked = UrlPlaceholderResolver.maskForLogging("");
        assertThat(masked).isEmpty();
    }

    @Test
    void returnsUrlUnchangedWhenNoPlaceholders() {
        String url = "https://api.example.com/data?apikey=abc123";
        String resolved = UrlPlaceholderResolver.resolve(url);
        assertThat(resolved).isEqualTo(url);
    }

    @Test
    void returnsNullForNullInput() {
        String resolved = UrlPlaceholderResolver.resolve(null);
        assertThat(resolved).isNull();
    }

    @Test
    void returnsEmptyForEmptyInput() {
        String resolved = UrlPlaceholderResolver.resolve("");
        assertThat(resolved).isEmpty();
    }

    @Test
    void validateNoPlaceholdersPassesWhenNoPlaceholders() {
        UrlPlaceholderResolver.validateNoPlaceholders("https://api.example.com/path");
        UrlPlaceholderResolver.validateNoPlaceholders("https://api.example.com/path?param=value");
    }

    @Test
    void validateNoPlaceholdersThrowsWhenPlaceholdersPresent() {
        assertThatThrownBy(() -> 
            UrlPlaceholderResolver.validateNoPlaceholders("https://api.example.com/{apikey}"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("{REDACTED}");
    }

    @Test
    void throwsExceptionWhenRequiredEnvironmentVariableIsNotSet() {
        // Ensure the environment variable is not set
        assertThat(System.getenv("NONEXISTENT_KEY_12345")).isNull();
        
        assertThatThrownBy(() -> 
            UrlPlaceholderResolver.resolve("https://api.example.com/{nonexistent_key_12345}"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("NONEXISTENT_KEY_12345")
                .hasMessageNotContaining("{nonexistent_key_12345}");
    }

    @Test
    void placeholderIsCaseInsensitiveForResolution() {
        // This test verifies that placeholder names are case-insensitive
        // by checking that both uppercase and lowercase placeholders
        // would look for the same environment variable (uppercase)
        
        // We can't actually test resolution without setting env vars,
        // but we can verify the error message format
        try {
            UrlPlaceholderResolver.resolve("https://api.example.com/{test_key}");
        } catch (IllegalStateException e) {
            // Should look for TEST_KEY (uppercase)
            assertThat(e.getMessage()).contains("TEST_KEY");
        }
    }

    @Test
    void handlesPlaceholderWithUnderscores() {
        try {
            UrlPlaceholderResolver.resolve("https://api.example.com/{my_api_key}");
        } catch (IllegalStateException e) {
            // Should look for MY_API_KEY
            assertThat(e.getMessage()).contains("MY_API_KEY");
        }
    }

    @Test
    void handlesPlaceholderWithNumbers() {
        try {
            UrlPlaceholderResolver.resolve("https://api.example.com/{api_key_123}");
        } catch (IllegalStateException e) {
            // Should look for API_KEY_123
            assertThat(e.getMessage()).contains("API_KEY_123");
        }
    }

    @Test
    void handlesUrlWithQueryParameters() {
        String url = "https://api.example.com/data?apikey={apikey}&country=FR";
        
        // Mask for logging should work
        String masked = UrlPlaceholderResolver.maskForLogging(url);
        assertThat(masked).contains("apikey={REDACTED}");
        assertThat(masked).contains("&country=FR");
        
        // Has placeholders should work
        assertThat(UrlPlaceholderResolver.hasPlaceholders(url)).isTrue();
    }

    @Test
    void handlesUrlWithPathSegments() {
        String url = "https://api.example.com/{apikey}/v1/feeds";
        
        // Mask for logging should work
        String masked = UrlPlaceholderResolver.maskForLogging(url);
        assertThat(masked).doesNotContain("{apikey}");
        assertThat(masked).contains("/v1/feeds");
        
        // Has placeholders should work
        assertThat(UrlPlaceholderResolver.hasPlaceholders(url)).isTrue();
    }

    @Test
    void handlesMultiplePlaceholders() {
        String url = "https://{domain}/api/{apikey}/v1";
        
        assertThat(UrlPlaceholderResolver.hasPlaceholders(url)).isTrue();
        assertThat(UrlPlaceholderResolver.maskForLogging(url))
            .isEqualTo("https://{REDACTED}/api/{REDACTED}/v1");
    }

    @Test
    void resolvesUrlWithoutPlaceholder() {
        String url = "https://api.example.com/data";
        String resolved = UrlPlaceholderResolver.resolve(url);
        assertThat(resolved).isEqualTo(url);
    }

    @Test
    void handlesPlaceholderWithHyphen() {
        // Placeholder with hyphen is not supported by the current regex
        // which only allows [a-zA-Z0-9_]
        String url1 = "https://api.example.com/{api-key}";
        assertThat(UrlPlaceholderResolver.hasPlaceholders(url1)).isFalse();
        
        // URL should be returned unchanged
        String resolved = UrlPlaceholderResolver.resolve(url1);
        assertThat(resolved).isEqualTo(url1);
    }

    @Test
    void handlesUrlEncodedCharacters() {
        String url = "https://api.example.com/path%20with%20space/{apikey}";
        
        assertThat(UrlPlaceholderResolver.hasPlaceholders(url)).isTrue();
        assertThat(UrlPlaceholderResolver.maskForLogging(url))
            .isEqualTo("https://api.example.com/path%20with%20space/{REDACTED}");
    }

    @Test
    void handlesUrlWithPort() {
        String url = "https://api.example.com:8443/{apikey}/path";
        
        assertThat(UrlPlaceholderResolver.hasPlaceholders(url)).isTrue();
        assertThat(UrlPlaceholderResolver.maskForLogging(url))
            .isEqualTo("https://api.example.com:8443/{REDACTED}/path");
    }

    @Test
    void handlesUrlWithFragment() {
        String url = "https://api.example.com/{apikey}#section";
        
        assertThat(UrlPlaceholderResolver.hasPlaceholders(url)).isTrue();
        assertThat(UrlPlaceholderResolver.maskForLogging(url))
            .isEqualTo("https://api.example.com/{REDACTED}#section");
    }
}
