package com.chanter.auth.moderation;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE+5)
public class ModerationRequestContext extends OncePerRequestFilter {
    public static final String CORRELATION="chanter.moderation.correlation";
    @Override protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain)
            throws ServletException,IOException {
        String path=request.getRequestURI().substring(request.getContextPath().length());
        if(path.startsWith("/api/v1/platform-admin") || path.startsWith("/api/v1/moderation/")
                || path.startsWith("/api/v1/auth/moderation-appeals")) {
            UUID correlation=UUID.randomUUID();
            request.setAttribute(CORRELATION,correlation);
            response.setHeader("X-Chanter-Request-Id",correlation.toString());
            response.setHeader("Cache-Control","no-store");
        }
        chain.doFilter(request,response);
    }
}
