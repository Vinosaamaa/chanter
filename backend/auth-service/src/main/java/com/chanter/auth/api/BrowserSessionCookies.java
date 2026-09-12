package com.chanter.auth.api;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Duration;
import java.time.Instant;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

@Component
public class BrowserSessionCookies {
    public static final String REFRESH_COOKIE = "chanter_refresh";
    public static final String OAUTH_COOKIE = "chanter_oauth_state";
    public void setRefresh(HttpServletResponse response, String token, Instant expiresAt) {
        write(response, REFRESH_COOKIE, token, Duration.ofSeconds(Math.max(0, Duration.between(Instant.now(), expiresAt).toSeconds())), "Strict");
    }

    public void clearRefresh(HttpServletResponse response) {
        write(response, REFRESH_COOKIE, "", Duration.ZERO, "Strict");
    }

    public void setOauthState(HttpServletResponse response, String state) {
        write(response, OAUTH_COOKIE, state, Duration.ofMinutes(10), "Lax");
    }

    public void clearOauthState(HttpServletResponse response) {
        write(response, OAUTH_COOKIE, "", Duration.ZERO, "Lax");
    }

    public String read(HttpServletRequest request, String name) {
        String value = null;
        if (request.getCookies() != null) {
            for (Cookie cookie : request.getCookies()) {
                if (name.equals(cookie.getName())) {
                    // Reject ambiguous same-name cookies instead of choosing an attacker-controlled path.
                    if (value != null) return "";
                    value = cookie.getValue();
                }
            }
        }
        return value;
    }

    private void write(HttpServletResponse response, String name, String value, Duration ttl, String sameSite) {
        response.addHeader(HttpHeaders.SET_COOKIE, ResponseCookie.from(name, value)
                .secure(true).httpOnly(true).sameSite(sameSite).path("/api/v1/auth").maxAge(ttl).build().toString());
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
    }
}
