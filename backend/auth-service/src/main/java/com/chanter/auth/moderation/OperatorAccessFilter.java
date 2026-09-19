package com.chanter.auth.moderation;

import com.chanter.common.auth.InvalidJwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Set;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.server.ResponseStatusException;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class OperatorAccessFilter extends OncePerRequestFilter {
    public static final String OPERATOR_ATTRIBUTE = OperatorAccessFilter.class.getName() + ".operator";
    private static final Set<String> VERIFICATION_PATHS = Set.of(
            "/api/v1/platform-admin/verification/enrollment",
            "/api/v1/platform-admin/verification/confirmation",
            "/api/v1/platform-admin/verification/challenge");
    private final OperatorAccess access;

    public OperatorAccessFilter(OperatorAccess access) { this.access = access; }

    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String path = request.getServletPath();
        if (path.isEmpty()) path = request.getRequestURI().substring(request.getContextPath().length());
        if (!path.equals("/api/v1/platform-admin") && !path.startsWith("/api/v1/platform-admin/")) {
            chain.doFilter(request, response);
            return;
        }
        response.setHeader("Cache-Control", "no-store");
        try {
            String authorization = request.getHeader("Authorization");
            var operator = VERIFICATION_PATHS.contains(path)
                    ? access.requireRole(authorization)
                    : access.requireStepUp(authorization, request.getHeader("X-Chanter-Operator-Verification"));
            request.setAttribute(OPERATOR_ATTRIBUTE, operator);
        } catch (InvalidJwtException invalid) {
            response.sendError(401, "Authentication required");
            return;
        } catch (ResponseStatusException denied) {
            response.sendError(denied.getStatusCode().value(), denied.getReason());
            return;
        }
        chain.doFilter(request, response);
    }
}
