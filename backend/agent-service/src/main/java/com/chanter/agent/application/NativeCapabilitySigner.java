package com.chanter.agent.application;

import com.chanter.common.auth.JwtTokenService.AccessSession;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.time.Clock;
import java.util.Arrays;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/** Deployment-owned signing identity. Never generates a production key or accepts one from a browser. */
@Component
public final class NativeCapabilitySigner {
    public static final int MAX_INPUT_BYTES = 32768;
    public static final int MAX_OUTPUT_BYTES = 8192;
    public static final int DEADLINE_MS = 45000;
    private final String origin;
    private final PrivateKey key;
    private final String publicKeyPem;
    private final Set<String> models;
    private final Clock clock;
    private static final ObjectMapper JSON = new ObjectMapper();

    public NativeCapabilitySigner(@Value("${chanter.native-companion.origin:}") String origin,
            @Value("${chanter.native-companion.private-key-pkcs8:}") String privateKey,
            @Value("${chanter.native-companion.public-key-spki:}") String publicKey,
            @Value("${chanter.native-companion.models:}") String models, Clock clock) {
        this.origin = origin; this.clock = clock;
        this.models = Arrays.stream(models.split(",")).map(String::trim).filter(value -> !value.isEmpty()).collect(Collectors.toUnmodifiableSet());
        if (origin.isBlank() && privateKey.isBlank() && publicKey.isBlank() && this.models.isEmpty()) {
            key = null; publicKeyPem = null; return;
        }
        try {
            URI uri = URI.create(origin);
            if (!"https".equals(uri.getScheme()) || uri.getHost() == null || !origin.equals("https://" + uri.getRawAuthority())
                    || uri.getUserInfo() != null || this.models.isEmpty() || this.models.size() > 20
                    || this.models.stream().anyMatch(value -> !value.matches("[a-zA-Z0-9][a-zA-Z0-9._:/-]{0,127}"))) throw new IllegalArgumentException();
            var factory = KeyFactory.getInstance("Ed25519");
            key = factory.generatePrivate(new PKCS8EncodedKeySpec(Base64.getDecoder().decode(privateKey)));
            var pub = factory.generatePublic(new X509EncodedKeySpec(Base64.getDecoder().decode(publicKey)));
            var proof = Signature.getInstance("Ed25519"); proof.initSign(key); proof.update(new byte[] { 1 });
            byte[] signature = proof.sign(); proof.initVerify(pub); proof.update(new byte[] { 1 });
            if (!proof.verify(signature)) throw new IllegalArgumentException();
            publicKeyPem = "-----BEGIN PUBLIC KEY-----\n" + Base64.getMimeEncoder(64, new byte[] { '\n' }).encodeToString(pub.getEncoded())
                    + "\n-----END PUBLIC KEY-----\n";
        } catch (Exception invalid) { throw new IllegalArgumentException("Native companion signing configuration is invalid"); }
    }

    public boolean available() { return key != null; }
    public String origin() { return origin; }
    public String publicKeyPem() { return publicKeyPem; }
    public Set<String> models() { return models; }

    public Ticket pair(AccessSession session, UUID installation) {
        return sign(base("pair", session, installation, UUID.randomUUID()));
    }
    public Ticket status(AccessSession session, UUID installation) {
        return sign(base("status", session, installation, UUID.randomUUID()));
    }

    public Ticket study(AccessSession session, UUID installation, UUID request, UUID question, String model, String prompt, String evidenceHash) {
        requireModel(model);
        if (prompt == null || prompt.isEmpty() || prompt.getBytes(StandardCharsets.UTF_8).length > MAX_INPUT_BYTES
                || evidenceHash == null || !evidenceHash.matches("[a-f0-9]{64}")) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Native evidence exceeds its bounds");
        var body = base("study", session, installation, request);
        body.put("questionId", question.toString()); body.put("provider", "codex"); body.put("mode", "quoted-evidence"); body.put("model", model);
        body.put("promptSha256", sha256(prompt)); body.put("evidenceSha256", java.util.List.of(evidenceHash));
        body.put("maxInputBytes", MAX_INPUT_BYTES); body.put("maxOutputBytes", MAX_OUTPUT_BYTES); body.put("deadlineMs", DEADLINE_MS);
        return sign(body);
    }

    public void requireModel(String model) {
        requireAvailable();
        if (!models.contains(model)) throw new ResponseStatusException(HttpStatus.CONFLICT, "Native model is not enabled by this deployment");
    }
    private Map<String, Object> base(String kind, AccessSession session, UUID installation, UUID request) {
        requireAvailable();
        long now = clock.millis(), expires = Math.min(now + ("pair".equals(kind) ? 120000 : 60000), session.expiresAt().toEpochMilli());
        if (expires <= now || session.sessionId() == null || installation == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Active native session required");
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("version", 1); body.put("kind", kind); body.put("origin", origin); body.put("installationId", installation.toString());
        body.put("userId", session.userId().toString()); body.put("sessionId", session.sessionId().toString());
        body.put("requestId", request.toString()); body.put("issuedAt", now); body.put("expiresAt", expires);
        return body;
    }
    private Ticket sign(Map<String, Object> body) {
        try {
            var encoding = Base64.getUrlEncoder().withoutPadding();
            String encoded = encoding.encodeToString(JSON.writeValueAsBytes(body));
            var signer = Signature.getInstance("Ed25519"); signer.initSign(key);
            signer.update(("chanter-native-v1." + encoded).getBytes(StandardCharsets.UTF_8));
            return new Ticket(encoded + "." + encoding.encodeToString(signer.sign()), (long) body.get("expiresAt"));
        } catch (Exception failure) { throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Native signing unavailable"); }
    }
    private void requireAvailable() {
        if (!available()) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Native companion is not configured");
    }
    public static String sha256(String value) {
        try { return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (Exception unavailable) { throw new IllegalStateException("Required digest unavailable"); }
    }
    public record Ticket(String ticket, long expiresAt) {}
}
