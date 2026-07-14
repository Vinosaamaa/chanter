package com.chanter.message.web;

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
        if (!request.getRequestURI().startsWith("/api/v1/")) {
            filterChain.doFilter(request, response);
            return;
        }

        if (!requiresAuthenticatedUser(request.getRequestURI())) {
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

    private static boolean requiresAuthenticatedUser(String uri) {
        return uri.contains("/friend-requests")
                || uri.contains("/friendships")
                || uri.contains("/direct-messages")
                || uri.contains("/direct-message-calls")
                || uri.contains("/user-blocks")
                || uri.endsWith("/messages")
                || uri.contains("/support-questions")
                || uri.contains("/instructor-dashboard")
                || uri.contains("/ta-queue")
                || uri.contains("/faq-candidates")
                || uri.contains("/approved-faqs")
                || uri.endsWith("/channel-summary");
    }
}
