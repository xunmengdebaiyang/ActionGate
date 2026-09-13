package com.actiongate.security;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
final class RateLimitingFilter extends OncePerRequestFilter {
    private final boolean enabled;
    private final int limit;
    private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();

    RateLimitingFilter(@Value("${actiongate.rate-limit.enabled:true}") boolean enabled,
                       @Value("${actiongate.rate-limit.requests-per-minute:60}") int limit) {
        if (limit < 1 || limit > 100_000) {
            throw new IllegalArgumentException("Rate limit must be between 1 and 100000 requests per minute");
        }
        this.enabled = enabled;
        this.limit = limit;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (!enabled || !request.getRequestURI().startsWith("/api/v1/")) {
            chain.doFilter(request, response);
            return;
        }
        String key = request.getHeader("Authorization");
        if (key == null || key.isBlank()) {
            key = request.getRemoteAddr();
        }
        long now = System.currentTimeMillis();
        if (windows.size() > 1024) {
            windows.entrySet().removeIf(entry -> now - entry.getValue().startedAt() >= Duration.ofMinutes(2).toMillis());
        }
        Window window = windows.compute(key, (ignored, current) -> {
            if (current == null || now - current.startedAt() >= Duration.ofMinutes(1).toMillis()) {
                return new Window(now, new AtomicInteger(1));
            }
            current.count().incrementAndGet();
            return current;
        });
        int used = window.count().get();
        response.setHeader("X-RateLimit-Limit", Integer.toString(limit));
        response.setHeader("X-RateLimit-Remaining", Integer.toString(Math.max(0, limit - used)));
        if (used > limit) {
            response.setStatus(429);
            response.setHeader("Retry-After", "60");
            return;
        }
        chain.doFilter(request, response);
    }

    private record Window(long startedAt, AtomicInteger count) { }
}
