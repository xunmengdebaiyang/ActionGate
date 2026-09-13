package com.actiongate.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.http.HttpMethod;

@Configuration
public class SecurityConfiguration {
    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, SecurityProperties properties) throws Exception {
        http.csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .addFilterBefore(new ApiKeyAuthenticationFilter(properties), UsernamePasswordAuthenticationFilter.class);
        if (!properties.enabled()) {
            http.authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
        } else {
            http.authorizeHttpRequests(auth -> auth
                    .requestMatchers("/api/v1/status", "/actuator/health", "/error").permitAll()
                    .requestMatchers(HttpMethod.POST, "/api/v1/runs/*/approval").hasAuthority("ACTION_APPROVE")
                    .requestMatchers(HttpMethod.POST, "/api/v1/runs").hasAuthority("RUN_SUBMIT")
                    .requestMatchers(HttpMethod.GET, "/api/v1/runs/*").hasAuthority("RUN_READ")
                    .anyRequest().denyAll());
        }
        return http.build();
    }
}
