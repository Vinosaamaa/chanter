package com.chanter.auth.moderation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class OperatorFactorTest {
    @Test void matchesRfc6238Sha1VectorsAndUsesLongCounter() {
        byte[] secret = "12345678901234567890".getBytes(StandardCharsets.US_ASCII);
        assertThat(OperatorFactor.code(secret, 59L / 30, 8)).isEqualTo("94287082");
        assertThat(OperatorFactor.code(secret, 1111111109L / 30, 8)).isEqualTo("07081804");
        assertThat(OperatorFactor.code(secret, 20000000000L / 30, 8)).isEqualTo("65353130");
    }

    @Test void storedSecretIsRandomizedAuthenticatedAndBoundToOperator() {
        var factor = new OperatorFactor(Base64.getEncoder().encodeToString(new byte[32]));
        UUID user = UUID.randomUUID();
        byte[] secret = new byte[20];
        String first = factor.encrypt(user, secret);
        String second = factor.encrypt(user, secret);
        assertThat(first).isNotEqualTo(second);
        assertThat(factor.decrypt(user, first)).isEqualTo(secret);
        assertThatThrownBy(() -> factor.decrypt(UUID.randomUUID(), first)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> factor.decrypt(user, first.substring(1))).isInstanceOf(IllegalStateException.class);
    }

    @Test void missingOperatorKeyDoesNotSilentlyStorePlaintext() {
        var factor = new OperatorFactor("");
        assertThatThrownBy(() -> factor.encrypt(UUID.randomUUID(), new byte[20]))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
    }
}
