package com.chanter.common.auth;

import java.nio.charset.StandardCharsets;

/**
 * Startup validation for {@code CHANTER_INTERNAL_SERVICE_TOKEN} (SEC-04 / SEC-21).
 *
 * <p>Constant-time compare of an empty configured token against an empty presented
 * token would otherwise succeed. Reject blank / short / known-default values when
 * the token is first loaded.
 */
public final class InternalServiceTokens {

    public static final int MIN_LENGTH = 32;

    /** Historical in-git example value — must never be accepted at runtime. */
    public static final String FORBIDDEN_DEFAULT =
            "chanter-local-dev-internal-service-token-32bytes!!";

    private InternalServiceTokens() {
    }

    public static String require(String token) {
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException(
                    "Internal service token must not be blank; set CHANTER_INTERNAL_SERVICE_TOKEN via make product-env");
        }
        if (token.length() < MIN_LENGTH) {
            throw new IllegalArgumentException(
                    "Internal service token must be at least " + MIN_LENGTH + " characters");
        }
        if (FORBIDDEN_DEFAULT.equals(token)) {
            throw new IllegalArgumentException(
                    "Internal service token rejects known default value; set CHANTER_INTERNAL_SERVICE_TOKEN via make product-env");
        }
        return token;
    }

    public static byte[] requireBytes(String token) {
        return require(token).getBytes(StandardCharsets.UTF_8);
    }
}
