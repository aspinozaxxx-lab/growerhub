package ru.growerhub.backend.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Locale;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import ru.growerhub.backend.api.ApiError;
import ru.growerhub.backend.user.UserEntity;
import ru.growerhub.backend.user.UserService;

@Component
public class JwtAuthFilter extends OncePerRequestFilter {
    private static final String BEARER_PREFIX = "bearer ";

    private final JwtService jwtService;
    private final UserService userService;
    private final ObjectMapper objectMapper;

    public JwtAuthFilter(JwtService jwtService, UserService userService, ObjectMapper objectMapper) {
        this.jwtService = jwtService;
        this.userService = userService;
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path == null || !path.startsWith("/api/");
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (authorization == null || !authorization.toLowerCase(Locale.ROOT).startsWith(BEARER_PREFIX)) {
            writeError(response, HttpServletResponse.SC_UNAUTHORIZED, "Not authenticated", true);
            return;
        }

        String token = authorization.substring(BEARER_PREFIX.length()).trim();
        if (token.isEmpty()) {
            writeError(response, HttpServletResponse.SC_UNAUTHORIZED, "Not authenticated", true);
            return;
        }

        Claims claims;
        try {
            claims = jwtService.parseToken(token);
        } catch (JwtException | IllegalArgumentException ex) {
            writeError(response, HttpServletResponse.SC_UNAUTHORIZED, "Ne udalos' raspoznavat' token", true);
            return;
        }

        Integer userId = parseUserId(claims.get("user_id"));
        if (userId == null) {
            writeError(response, HttpServletResponse.SC_UNAUTHORIZED, "Ne udalos' raspoznavat' token", true);
            return;
        }

        UserEntity user = userService.findById(userId).orElse(null);
        if (user == null) {
            writeError(response, HttpServletResponse.SC_UNAUTHORIZED, "Polzovatel' ne najden", true);
            return;
        }
        if (!user.isActive()) {
            writeError(response, HttpServletResponse.SC_FORBIDDEN, "Polzovatel' otkljuchen", false);
            return;
        }

        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(user, null, java.util.List.of());
        SecurityContextHolder.getContext().setAuthentication(authentication);
        filterChain.doFilter(request, response);
    }

    private Integer parseUserId(Object rawValue) {
        if (rawValue == null) {
            return null;
        }
        if (rawValue instanceof Integer value) {
            return value;
        }
        if (rawValue instanceof Long value) {
            if (value > Integer.MAX_VALUE || value < Integer.MIN_VALUE) {
                return null;
            }
            return value.intValue();
        }
        if (rawValue instanceof Number value) {
            return value.intValue();
        }
        if (rawValue instanceof String value) {
            try {
                return Integer.parseInt(value);
            } catch (NumberFormatException ex) {
                return null;
            }
        }
        return null;
    }

    private void writeError(HttpServletResponse response, int status, String detail, boolean includeWwwAuth)
            throws IOException {
        SecurityContextHolder.clearContext();
        response.resetBuffer();
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        if (includeWwwAuth) {
            response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
        }
        objectMapper.writeValue(response.getOutputStream(), new ApiError(detail));
        response.flushBuffer();
    }
}
