package ru.growerhub.backend.common.config.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import ru.growerhub.backend.common.contract.ApiError;
import ru.growerhub.backend.common.contract.AuthenticatedDevice;
import ru.growerhub.backend.device.DeviceFacade;

@Component
public class DeviceAuthFilter extends OncePerRequestFilter {
    private static final String DEVICE_PREFIX = "Device ";
    private static final Pattern DEVICE_PATH = Pattern.compile(
            "^/api/device/([^/]+)/(status|settings|firmware)/?$"
    );

    private final DeviceFacade deviceFacade;
    private final ObjectMapper objectMapper;

    public DeviceAuthFilter(DeviceFacade deviceFacade, ObjectMapper objectMapper) {
        this.deviceFacade = deviceFacade;
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
        return authorization == null || !authorization.startsWith(DEVICE_PREFIX);
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        Matcher matcher = DEVICE_PATH.matcher(request.getRequestURI());
        if (!matcher.matches() || !isAllowedMethod(request.getMethod(), matcher.group(2))) {
            writeUnauthorized(response);
            return;
        }

        String deviceId = URLDecoder.decode(matcher.group(1), StandardCharsets.UTF_8);
        String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
        String rawToken = authorization.substring(DEVICE_PREFIX.length()).trim();
        if (!deviceFacade.authenticateDevice(deviceId, rawToken)) {
            writeUnauthorized(response);
            return;
        }

        AuthenticatedDevice principal = new AuthenticatedDevice(deviceId);
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(principal, null, java.util.List.of());
        SecurityContextHolder.getContext().setAuthentication(authentication);
        filterChain.doFilter(request, response);
    }

    private boolean isAllowedMethod(String method, String operation) {
        if ("status".equals(operation)) {
            return "POST".equalsIgnoreCase(method);
        }
        return ("settings".equals(operation) || "firmware".equals(operation))
                && "GET".equalsIgnoreCase(method);
    }

    private void writeUnauthorized(HttpServletResponse response) throws IOException {
        SecurityContextHolder.clearContext();
        response.resetBuffer();
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Device");
        objectMapper.writeValue(response.getOutputStream(), new ApiError("Not authenticated"));
        response.flushBuffer();
    }
}
