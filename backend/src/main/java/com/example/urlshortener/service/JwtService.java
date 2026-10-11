package com.example.urlshortener.service;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
public class JwtService {

    @Value("${app.jwt.secret}")
    private String secret;

    @Value("${app.jwt.access-token-ttl-minutes:15}")
    private long accessTokenTtlMinutes;

    @Value("${app.jwt.issuer:url-shortener}")
    private String issuer;

    @Value("${app.jwt.audience:url-shortener-client}")
    private String audience;

    private SecretKey signingKey;

    @PostConstruct
    public void init() {
        // Ensure secret is at least 256 bits (32 chars) for HS256
        if (secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            log.warn("JWT secret is less than 256 bits. Using padded secret for development only.");
            secret = String.format("%-32s", secret).replace(' ', '0');
        }
        this.signingKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Generate a short-lived JWT access token.
     */
    public String generateAccessToken(Long userId, String email, String role) {
        Instant now = Instant.now();
        Instant expiry = now.plusSeconds(accessTokenTtlMinutes * 60);

        return Jwts.builder()
                .subject(userId.toString())
                .claim("email", email)
                .claim("role", role)
                .issuer(issuer)
                .audience().add(audience).and()
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiry))
                .id(UUID.randomUUID().toString()) // jti for potential blacklisting
                .signWith(signingKey, Jwts.SIG.HS256)
                .compact();
    }

    /**
     * Validate and parse JWT token. Returns claims if valid.
     */
    public Jws<Claims> validateToken(String token) {
        return Jwts.parser()
                .verifyWith(signingKey)
                .requireIssuer(issuer)
                .requireAudience(audience)
                .build()
                .parseSignedClaims(token);
    }

    /**
     * Extract user ID from token without full validation (for logging/metrics).
     * Use validateToken() for authorization decisions.
     */
    public Long extractUserId(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(signingKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            return Long.valueOf(claims.getSubject());
        } catch (JwtException e) {
            return null;
        }
    }

    /**
     * Extract all claims from token.
     */
    public Claims extractClaims(String token) {
        return validateToken(token).getPayload();
    }

    /**
     * Check if token is expired.
     */
    public boolean isTokenExpired(String token) {
        try {
            Claims claims = extractClaims(token);
            return claims.getExpiration().before(new Date());
        } catch (JwtException e) {
            return true;
        }
    }

    /**
     * Get remaining TTL in seconds.
     */
    public long getRemainingTtl(String token) {
        try {
            Claims claims = extractClaims(token);
            long expiresAt = claims.getExpiration().getTime();
            long now = System.currentTimeMillis();
            return Math.max(0, (expiresAt - now) / 1000);
        } catch (JwtException e) {
            return 0;
        }
    }
}