package com.chanter.auth.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

class AuthPasswordEncodingTest {
    @Test
    void advertisedLongAndUnicodePasswordsPreserveTheirCompleteValue() {
        var encoder = new AuthServiceConfig().passwordEncoder();
        for (String password : new String[] { "a".repeat(127) + "1", "界".repeat(80) }) {
            String encoded = encoder.encode(password);
            assertThat(encoder.matches(password, encoded)).isTrue();
            assertThat(encoder.matches(password.substring(0, password.length() - 1) + "2", encoded)).isFalse();
        }
    }

    @Test
    void existingUnprefixedBcryptHashesRemainUsable() {
        String existing = new BCryptPasswordEncoder().encode("existing-account-password");
        var encoder = new AuthServiceConfig().passwordEncoder();
        assertThat(encoder.matches("existing-account-password", existing)).isTrue();
        assertThat(encoder.matches("wrong-account-password", existing)).isFalse();
    }
}
