package ru.growerhub.backend.config.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Locale;
import java.util.Set;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;
import ru.growerhub.backend.auth.AuthFacade;
import ru.growerhub.backend.common.contract.ApiError;
import ru.growerhub.backend.common.contract.AuthenticatedUser;
import ru.growerhub.backend.user.UserFacade;

@Component
public class JwtAuthFilter extends OncePerRequestFilter {
    private static final String BEARER_PREFIX = "bearer ";
    private static final AntPathMatcher PATH_MATCHER = new AntPathMatcher();
    private static final Set<String> PUBLIC_PATHS = Set.of(
            "/api/auth/login",
            "/api/auth/refresh",
            "/api/auth/logout"
    );

    private final AuthFacade authFacade;
    private final UserFacade userFacade;
    private final ObjectMapper objectMapper;

    public JwtAuthFilter(AuthFacade authFacade, UserFacade userFacade, ObjectMapper objectMapper) {
        this.authFacade = authFacade;
        this.userFacade = userFacade;
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        if (path == null || !path.startsWith("/api/")) {
            return true;
        }
        if (PUBLIC_PATHS.contains(path)) {
            return true;
        }
        if (path.startsWith("/api/auth/sso/")) {
            return true;
        }
        return isPublicDevicePath(request.getMethod(), path);
    }

    private boolean isPublicDevicePath(String method, String path) {
        if ("POST".equalsIgnoreCase(method) && (PATH_MATCHER.match("/api/device/*/status", path)
                || PATH_MATCHER.match("/api/device/*/status/", path))) {
            return true;
        }
        if ("GET".equalsIgnoreCase(method) && PATH_MATCHER.match("/api/device/*/firmware", path)) {
            return true;
        }
        if ("POST".equalsIgnoreCase(method) && "/api/upload-firmware".equals(path)) {
            return true;
        }
        if ("POST".equalsIgnoreCase(method) && PATH_MATCHER.match("/api/device/*/trigger-update", path)) {
            return true;
        }
        if ("GET".equalsIgnoreCase(method) && "/api/firmware/versions".equals(path)) {
            return true;
        }
        if ("GET".equalsIgnoreCase(method) && PATH_MATCHER.match("/api/device/*/settings", path)) {
            return true;
        }
        if ("PUT".equalsIgnoreCase(method) && PATH_MATCHER.match("/api/device/*/settings", path)) {
            return true;
        }
        if ("GET".equalsIgnoreCase(method) && "/api/devices".equals(path)) {
            return true;
        }
        return "DELETE".equalsIgnoreCase(method) && PATH_MATCHER.match("/api/device/*", path);
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

        Integer userId = authFacade.parseUserId(token);
        if (userId == null) {
            writeError(response, HttpServletResponse.SC_UNAUTHORIZED, "Ne udalos' raspoznavat' token", true);
            return;
        }

        UserFacade.AuthUser user = userFacade.getAuthUser(userId);
        if (user == null) {
            writeError(response, HttpServletResponse.SC_UNAUTHORIZED, "Polzovatel' ne najden", true);
            return;
        }
        if (!user.active()) {
            writeError(response, HttpServletResponse.SC_FORBIDDEN, "Polzovatel' otkljuchen", false);
            return;
        }

        AuthenticatedUser principal = new AuthenticatedUser(user.id(), user.role());
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(principal, null, java.util.List.of());
        SecurityContextHolder.getContext().setAuthentication(authentication);
        filterChain.doFilter(request, response);
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

