package com.eparly.api.auth;

import com.eparly.api.auth.config.AuthConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;

class PasswordEncoderTest {

    @Test
    void storesAnArgon2HashAndMatchesOnlyTheOriginalPassword() {
        PasswordEncoder encoder = new AuthConfiguration().passwordEncoder();

        String hash = encoder.encode("a-long-test-password-2026");

        assertThat(hash).startsWith("$argon2id$");
        assertThat(hash).isNotEqualTo("a-long-test-password-2026");
        assertThat(encoder.matches("a-long-test-password-2026", hash)).isTrue();
        assertThat(encoder.matches("another-password", hash)).isFalse();
    }
}
