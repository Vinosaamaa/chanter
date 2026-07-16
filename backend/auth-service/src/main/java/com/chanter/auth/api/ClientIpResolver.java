package com.chanter.auth.api;

/**
 * Resolves the original client IP when auth-service sits behind the gateway (SEC-08).
 */
final class ClientIpResolver {

    private ClientIpResolver() {
    }

    static String resolve(jakarta.servlet.http.HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            String firstHop = forwarded.split(",")[0].trim();
            if (!firstHop.isEmpty()) {
                return firstHop;
            }
        }
        String remote = request.getRemoteAddr();
        return remote == null || remote.isBlank() ? "unknown" : remote;
    }
}
