package com.chanter.auth.api;

import com.chanter.auth.application.TurnstileVerification;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public final class HumanVerificationFilter extends OncePerRequestFilter {
    private final TurnstileVerification verification;
    public HumanVerificationFilter(TurnstileVerification verification) { this.verification = verification; }

    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        // Spring route matching ignores matrix parameters; policy matching must never disagree with it.
        if (path.contains(";") || path.toLowerCase(java.util.Locale.ROOT).contains("%3b")) {
            response.setStatus(400);
            response.setHeader("Cache-Control", "no-store");
            return;
        }
        boolean registration = path.equals("/api/v1/auth/register");
        boolean recovery = path.equals("/api/v1/auth/forgot-password");
        if (!verification.options().enabled() || !"POST".equals(request.getMethod()) || !(registration || recovery)
                || "email".equals(request.getHeader("X-Chanter-Verification-Method"))) {
            chain.doFilter(request, response);
            return;
        }
        var result = verification.verify(request.getHeader("X-Chanter-Bot-Token"), registration ? "register" : "recovery");
        if (result == TurnstileVerification.Result.PASSED) { chain.doFilter(request, response); return; }
        response.setStatus(result == TurnstileVerification.Result.UNAVAILABLE ? 503 : 428);
        response.setContentType("application/json");
        response.setHeader("Cache-Control", "no-store");
        response.getWriter().write("{\"code\":\"VERIFICATION_REQUIRED\",\"message\":\"Complete the verification or choose email verification instead.\"}");
    }
}
