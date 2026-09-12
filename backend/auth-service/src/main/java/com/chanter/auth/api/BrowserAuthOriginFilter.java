package com.chanter.auth.api;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** Cookies are accepted only from explicitly trusted browser origins with a non-simple request header. */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class BrowserAuthOriginFilter extends OncePerRequestFilter {
    private final Set<String> allowedOrigins;

    public BrowserAuthOriginFilter(@Value("${chanter.auth.allowed-origins:http://localhost:5173}") String origins) {
        allowedOrigins = Arrays.stream(origins.split(",")).map(String::trim).collect(Collectors.toUnmodifiableSet());
        for (String origin : allowedOrigins) {
            URI uri = URI.create(origin);
            if (uri.getHost() == null || uri.getUserInfo() != null || uri.getQuery() != null || uri.getFragment() != null
                    || (uri.getRawPath() != null && !uri.getRawPath().isEmpty())
                    || !("https".equals(uri.getScheme()) || ("http".equals(uri.getScheme())
                    && Set.of("localhost", "127.0.0.1", "[::1]").contains(uri.getHost())))) {
                throw new IllegalArgumentException("Auth allowed origins must be exact HTTPS origins or local loopback HTTP origins");
            }
        }
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        if (path.startsWith("/api/v1/auth/")) {
            response.setHeader("Cache-Control", "no-store");
            String method = request.getMethod();
            // This endpoint authenticates with an explicit bearer token and is called by services too.
            boolean bearerOnly = "/api/v1/auth/profiles/query".equals(path);
            if (!bearerOnly && !Set.of("GET", "HEAD", "OPTIONS").contains(method)) {
                String origin = request.getHeader("Origin");
                if (origin == null || !allowedOrigins.contains(origin)
                        || !"1".equals(request.getHeader("X-Chanter-CSRF"))) {
                    response.sendError(HttpServletResponse.SC_FORBIDDEN, "Untrusted browser request");
                    return;
                }
            }
        }
        chain.doFilter(request, response);
    }
}
