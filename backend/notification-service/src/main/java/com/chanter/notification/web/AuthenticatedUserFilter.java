package com.chanter.notification.web;

import com.chanter.common.auth.AuthHeaders;
import com.chanter.common.auth.AuthRequestAttributes;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class AuthenticatedUserFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String uri = request.getRequestURI();
        if (!uri.startsWith("/api/v1/") || uri.startsWith("/api/v1/internal/")) {
            filterChain.doFilter(request, response);
            return;
        }

        String userIdHeader = request.getHeader(AuthHeaders.USER_ID);
        if (userIdHeader == null || userIdHeader.isBlank()) {
            response.sendError(HttpStatus.UNAUTHORIZED.value(), "Authentication required");
            return;
        }

        try {
            request.setAttribute(AuthRequestAttributes.USER_ID, UUID.fromString(userIdHeader));
        } catch (IllegalArgumentException exception) {
            response.sendError(HttpStatus.UNAUTHORIZED.value(), "Invalid authenticated user id");
            return;
        }

        filterChain.doFilter(request, response);
    }
}
