package ru.growerhub.backend.auth;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class JwtService {
    private final Key signingKey;
    private final AuthSettings authSettings;

    public JwtService(AuthSettings authSettings) {
        this.authSettings = authSettings;
        this.signingKey = Keys.hmacShaKeyFor(authSettings.getSecretKey().getBytes(StandardCharsets.UTF_8));
    }

    public Claims parseToken(String token) throws JwtException {
        return Jwts.parserBuilder()
                .setSigningKey(signingKey)
                .build()
                .parseClaimsJws(token)
                .getBody();
    }

    public String createToken(Map<String, Object> claims, Duration ttl) {
        Instant now = Instant.now();
        Instant expiry = now.plus(ttl);
        return Jwts.builder()
                .setClaims(claims)
                .setExpiration(Date.from(expiry))
                .signWith(signingKey, SignatureAlgorithm.HS256)
                .compact();
    }

    public String createAccessToken(Map<String, Object> claims) {
        return createToken(claims, Duration.ofMinutes(authSettings.getAccessTokenExpireMinutes()));
    }
}
