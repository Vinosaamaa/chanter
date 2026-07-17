package com.chanter.common.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class InternalServiceTokensTest {

    @Test
    void acceptsStrongToken() {
        String token = "test-internal-service-token-for-auth";
        assertThat(InternalServiceTokens.require(token)).isEqualTo(token);
        assertThat(InternalServiceTokens.requireBytes(token)).hasSize(token.length());
    }

    @Test
    void rejectsBlank() {
        assertThatThrownBy(() -> InternalServiceTokens.require(""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must not be blank");
        assertThatThrownBy(() -> InternalServiceTokens.require("   "))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> InternalServiceTokens.require(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsShortToken() {
        assertThatThrownBy(() -> InternalServiceTokens.require("too-short-token-value!!!!"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("at least");
    }

    @Test
    void rejectsKnownDefault() {
        assertThatThrownBy(() -> InternalServiceTokens.require(InternalServiceTokens.FORBIDDEN_DEFAULT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("known default");
    }
}
