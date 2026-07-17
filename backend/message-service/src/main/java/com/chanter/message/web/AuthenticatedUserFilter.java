package com.chanter.message.web;

import com.chanter.common.auth.InternalServiceTokens;
import com.chanter.common.auth.AuthHeaders;
import com.chanter.common.auth.AuthRequestAttributes;
import com.chanter.common.auth.InvalidJwtException;
import com.chanter.common.auth.JwtTokenService;
import com.chanter.common.auth.RequestIdentity;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class AuthenticatedUserFilter extends OncePerRequestFilter {

    private final JwtTokenService jwtTokenService;
    private final byte[] internalServiceToken;

    public AuthenticatedUserFilter(
            JwtTokenService jwtTokenService,
            @Value("${chanter.internal-service-token}") String internalServiceToken
    ) {
        this.jwtTokenService = jwtTokenService;
        this.internalServiceToken = InternalServiceTokens.requireBytes(internalServiceToken);
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String uri = request.getRequestURI();
        if (!uri.startsWith("/api/v1/")) {
            filterChain.doFilter(request, response);
            return;
        }

        if (!requiresAuthenticatedUser(uri)) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            UUID userId = RequestIdentity.requireUserId(
                    request.getHeader(HttpHeaders.AUTHORIZATION),
                    request.getHeader(AuthHeaders.USER_ID),
                    request.getHeader(AuthHeaders.INTERNAL_SERVICE_TOKEN),
                    internalServiceToken,
                    jwtTokenService
            );
            request.setAttribute(AuthRequestAttributes.USER_ID, userId);
            filterChain.doFilter(new UserIdInjectingWrapper(request, userId), response);
        } catch (InvalidJwtException | IllegalArgumentException exception) {
            response.sendError(HttpStatus.UNAUTHORIZED.value(), "Authentication required");
        }
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

    private static final class UserIdInjectingWrapper extends HttpServletRequestWrapper {

        private final String userId;

        UserIdInjectingWrapper(HttpServletRequest request, UUID userId) {
            super(request);
            this.userId = userId.toString();
        }

        @Override
        public String getHeader(String name) {
            if (AuthHeaders.USER_ID.equalsIgnoreCase(name)) {
                return userId;
            }
            return super.getHeader(name);
        }
    }
}
