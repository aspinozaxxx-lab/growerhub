﻿﻿﻿package ru.growerhub.backend.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import ru.growerhub.backend.common.config.security.JwtAuthFilter;

@Configuration
public class SecurityConfig {
    private final JwtAuthFilter jwtAuthFilter;

    public SecurityConfig(JwtAuthFilter jwtAuthFilter) {
        this.jwtAuthFilter = jwtAuthFilter;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/health").permitAll()
                        .requestMatchers(
                                "/api/auth/login",
                                "/api/auth/refresh",
                                "/api/auth/logout",
                                "/api/auth/sso/**"
                        ).permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/device/*/status", "/api/device/*/status/").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/device/*/settings").permitAll()
                        .requestMatchers(HttpMethod.PUT, "/api/device/*/settings").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/device/*/firmware").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/upload-firmware").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/device/*/trigger-update").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/firmware/versions").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/devices").permitAll()
                        .requestMatchers(HttpMethod.DELETE, "/api/device/*").permitAll()
                        .requestMatchers("/api/**").authenticated()
                        .anyRequest().permitAll()
                );

        http.addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
