package com.chanter.agent.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.chanter.common.auth.JwtTokenService.AccessSession;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class NativeCapabilitySignerTest {
    @Test
    void ticketsBindExactNativeSchemaAndUseDomainSeparatedEd25519Signatures() throws Exception {
        var keys = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        Instant now = Instant.parse("2026-09-19T00:00:00Z");
        var signer = new NativeCapabilitySigner("https://chanter.example", Base64.getEncoder().encodeToString(keys.getPrivate().getEncoded()),
                Base64.getEncoder().encodeToString(keys.getPublic().getEncoded()), "fixture-model", Clock.fixed(now, ZoneOffset.UTC));
        var session = new AccessSession(UUID.randomUUID(), UUID.randomUUID(), now.plusSeconds(20));
        UUID installation = UUID.randomUUID();
        var ticket = signer.study(session, installation, UUID.randomUUID(), UUID.randomUUID(), "fixture-model", "approved prompt", "0".repeat(64));
        String[] parts = ticket.ticket().split("\\.");
        var verifier = Signature.getInstance("Ed25519"); verifier.initVerify(keys.getPublic());
        verifier.update(("chanter-native-v1." + parts[0]).getBytes(StandardCharsets.UTF_8));
        assertThat(verifier.verify(Base64.getUrlDecoder().decode(parts[1]))).isTrue();
        var body = new ObjectMapper().readTree(Base64.getUrlDecoder().decode(parts[0]));
        assertThat(body.size()).isEqualTo(18);
        assertThat(body.path("sessionId").asText()).isEqualTo(session.sessionId().toString());
        assertThat(body.path("installationId").asText()).isEqualTo(installation.toString());
        assertThat(body.path("expiresAt").asLong()).isEqualTo(session.expiresAt().toEpochMilli());
        assertThat(body.path("mode").asText()).isEqualTo("quoted-evidence");
        assertThat(body.path("promptSha256").asText()).hasSize(64);
        assertThat(body.path("evidenceSha256").isArray()).isTrue();
        assertThat(body.path("evidenceSha256").get(0).asText()).isEqualTo("0".repeat(64));
        assertThatThrownBy(() -> signer.study(session, installation, UUID.randomUUID(), UUID.randomUUID(), "unconfigured", "prompt", "0".repeat(64)))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void missingConfigurationNeverCreatesAnEphemeralSigningIdentity() {
        var signer = new NativeCapabilitySigner("", "", "", "", Clock.systemUTC());
        assertThat(signer.available()).isFalse();
        assertThatThrownBy(() -> signer.pair(new AccessSession(UUID.randomUUID(), UUID.randomUUID(), Instant.now().plusSeconds(30)), UUID.randomUUID()))
                .isInstanceOf(ResponseStatusException.class);
    }
}
