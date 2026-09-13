package com.actiongate.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

final class ApiKeyAuthenticationFilter extends OncePerRequestFilter {
    private final SecurityProperties properties;

    ApiKeyAuthenticationFilter(SecurityProperties properties) {
        this.properties = properties;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (!properties.enabled() || "/api/v1/status".equals(request.getRequestURI())
                || !request.getRequestURI().startsWith("/api/v1/")) {
            chain.doFilter(request, response);
            return;
        }
        String token = request.getHeader("Authorization");
        if (token == null || !token.startsWith("Bearer ")) {
            response.setHeader("WWW-Authenticate", "Bearer");
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Authentication required");
            return;
        }
        String value = token.substring("Bearer ".length()).trim();
        boolean approval = matches(value, properties.approvalApiKey());
        boolean regular = matches(value, properties.apiKey());
        if (!approval && !regular) {
            response.setHeader("WWW-Authenticate", "Bearer");
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Invalid credentials");
            return;
        }
        var authorities = approval
                ? List.of(new SimpleGrantedAuthority("RUN_SUBMIT"), new SimpleGrantedAuthority("RUN_READ"),
                new SimpleGrantedAuthority("ACTION_APPROVE"))
                : List.of(new SimpleGrantedAuthority("RUN_SUBMIT"), new SimpleGrantedAuthority("RUN_READ"));
        var authentication = new UsernamePasswordAuthenticationToken("api-key", null, authorities);
        SecurityContextHolder.getContext().setAuthentication(authentication);
        chain.doFilter(request, response);
    }

    private static boolean matches(String supplied, String expected) {
        return expected != null && !expected.isBlank()
                && MessageDigest.isEqual(supplied.getBytes(StandardCharsets.UTF_8),
                expected.getBytes(StandardCharsets.UTF_8));
    }
}
