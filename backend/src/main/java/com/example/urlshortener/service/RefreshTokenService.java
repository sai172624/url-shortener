package com.example.urlshortener.service;

import com.example.urlshortener.entity.RefreshToken;
import com.example.urlshortener.exception.CustomExceptions;
import com.example.urlshortener.repository.RefreshTokenRepository;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class RefreshTokenService {

    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtService jwtService;

    @Value("${app.jwt.refresh-token-ttl-days:30}")
    private long refreshTokenTtlDays;

    @Value("${app.base-url:http://localhost:8080}")
    private String baseUrl;

    private static final String REFRESH_TOKEN_COOKIE_NAME = "refreshToken";
    private static final int REFRESH_TOKEN_BYTES = 32;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final BCryptPasswordEncoder BCRYPT = new BCryptPasswordEncoder(12);

    /**
     * Generate a new opaque refresh token and store its hash.
     * Returns the raw token (for cookie) and the saved entity.
     */
    @Transactional
    public RefreshTokenResult createRefreshToken(Long userId, String userAgent, String clientIp) {
        // Generate opaque token
        byte[] randomBytes = new byte[REFRESH_TOKEN_BYTES];
        SECURE_RANDOM.nextBytes(randomBytes);
        String rawToken = Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);

        // Hash for storage (SHA-256, not BCrypt - we need fast verification, not slow)
        String tokenHash = hashToken(rawToken);

        // Token family for rotation tracking
        UUID tokenFamily = UUID.randomUUID();

        OffsetDateTime now = OffsetDateTime.now();
        OffsetDateTime expiresAt = now.plusDays(refreshTokenTtlDays);

        RefreshToken refreshToken = RefreshToken.builder()
                .userId(userId)
                .tokenHash(tokenHash)
                .tokenFamily(tokenFamily)
                .issuedAt(now)
                .expiresAt(expiresAt)
                .userAgent(userAgent)
                .ipHash(hashIp(clientIp))
                .build();

        refreshTokenRepository.save(refreshToken);

        log.debug("Created refresh token for user {} with family {}", userId, tokenFamily);

        return new RefreshTokenResult(rawToken, refreshToken);
    }

    /**
     * Validate refresh token from cookie, rotate it (revoke old, issue new).
     * Returns new access token and new refresh token cookie.
     */
    @Transactional
    public RefreshTokenResult rotateRefreshToken(String rawToken, String userAgent, String clientIp,
                                                  HttpServletResponse response) {
        String tokenHash = hashToken(rawToken);

        RefreshToken storedToken = refreshTokenRepository.findByTokenHash(tokenHash)
                .orElseThrow(() -> new CustomExceptions.TokenRevokedException("Invalid refresh token"));

        if (!storedToken.isValid()) {
            // Token expired or revoked - revoke entire family for security
            revokeFamily(storedToken.getTokenFamily());
            throw new CustomExceptions.TokenRevokedException("Refresh token expired or revoked");
        }

        // Optional: Check for token reuse (potential theft detection)
        if (storedToken.getRevokedAt() != null) {
            revokeFamily(storedToken.getTokenFamily());
            throw new CustomExceptions.TokenRevokedException("Refresh token reuse detected - possible theft");
        }

        Long userId = storedToken.getUserId();
        UUID tokenFamily = storedToken.getTokenFamily();

        // REVOKE OLD TOKEN
        refreshTokenRepository.revokeByTokenHash(tokenHash, OffsetDateTime.now());

        // ISSUE NEW TOKEN (same family)
        byte[] randomBytes = new byte[REFRESH_TOKEN_BYTES];
        SECURE_RANDOM.nextBytes(randomBytes);
        String newRawToken = Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
        String newTokenHash = hashToken(newRawToken);

        OffsetDateTime now = OffsetDateTime.now();
        OffsetDateTime expiresAt = now.plusDays(refreshTokenTtlDays);

        RefreshToken newToken = RefreshToken.builder()
                .userId(userId)
                .tokenHash(newTokenHash)
                .tokenFamily(tokenFamily) // SAME family
                .issuedAt(now)
                .expiresAt(expiresAt)
                .userAgent(userAgent)
                .ipHash(hashIp(clientIp))
                .build();

        refreshTokenRepository.save(newToken);

        log.debug("Rotated refresh token for user {} family {}", userId, tokenFamily);

        return new RefreshTokenResult(newRawToken, newToken);
    }

    /**
     * Revoke a specific refresh token (logout single session).
     */
    @Transactional
    public void revokeToken(String rawToken) {
        String tokenHash = hashToken(rawToken);
        int updated = refreshTokenRepository.revokeByTokenHash(tokenHash, OffsetDateTime.now());
        if (updated > 0) {
            log.debug("Revoked single refresh token");
        }
    }

    /**
     * Revoke all tokens in a family (logout all sessions for a user).
     */
    @Transactional
    public void revokeFamily(UUID tokenFamily) {
        int updated = refreshTokenRepository.revokeByTokenFamily(tokenFamily, OffsetDateTime.now());
        if (updated > 0) {
            log.debug("Revoked {} tokens in family {}", updated, tokenFamily);
        }
    }

    /**
     * Revoke all tokens for a user (password change, security event).
     */
    @Transactional
    public void revokeAllForUser(Long userId) {
        int updated = refreshTokenRepository.revokeAllByUserId(userId, OffsetDateTime.now());
        if (updated > 0) {
            log.debug("Revoked all {} tokens for user {}", updated, userId);
        }
    }

    /**
     * Validate refresh token without rotation (for introspection).
     */
    @Transactional(readOnly = true)
    public Optional<RefreshToken> validateToken(String rawToken) {
        String tokenHash = hashToken(rawToken);
        return refreshTokenRepository.findByTokenHash(tokenHash)
                .filter(RefreshToken::isValid);
    }

    /**
     * Set refresh token as HttpOnly cookie.
     */
    public void setRefreshTokenCookie(HttpServletResponse response, String rawToken, long maxAgeSeconds) {
        Cookie cookie = new Cookie(REFRESH_TOKEN_COOKIE_NAME, rawToken);
        cookie.setHttpOnly(true);
        cookie.setSecure(true); // Requires HTTPS in production
        cookie.setPath("/api/v1/auth/refresh"); // Only sent to refresh endpoint
        cookie.setMaxAge((int) maxAgeSeconds);
        cookie.setAttribute("SameSite", "Strict");
        response.addCookie(cookie);
    }

    /**
     * Clear refresh token cookie (logout).
     */
    public void clearRefreshTokenCookie(HttpServletResponse response) {
        Cookie cookie = new Cookie(REFRESH_TOKEN_COOKIE_NAME, "");
        cookie.setHttpOnly(true);
        cookie.setSecure(true);
        cookie.setPath("/api/v1/auth/refresh");
        cookie.setMaxAge(0);
        cookie.setAttribute("SameSite", "Strict");
        response.addCookie(cookie);
    }

    /**
     * Extract refresh token from request cookie.
     */
    public Optional<String> extractRefreshToken(HttpServletRequest request) {
        if (request.getCookies() == null) {
            return Optional.empty();
        }
        for (Cookie cookie : request.getCookies()) {
            if (REFRESH_TOKEN_COOKIE_NAME.equals(cookie.getName())) {
                return Optional.ofNullable(cookie.getValue());
            }
        }
        return Optional.empty();
    }

    /**
     * Clean up expired and revoked tokens (run as scheduled job).
     */
    @Transactional
    public int cleanupExpiredTokens() {
        int deleted = refreshTokenRepository.deleteExpiredAndRevoked(OffsetDateTime.now());
        if (deleted > 0) {
            log.info("Cleaned up {} expired/revoked refresh tokens", deleted);
        }
        return deleted;
    }

    /**
     * Hash token with SHA-256 for storage.
     */
    private String hashToken(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(token.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    /**
     * Hash IP address for privacy (don't store raw IPs).
     */
    private String hashIp(String ip) {
        if (ip == null || ip.isEmpty()) {
            return null;
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(ip.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    /**
     * Result container for refresh token operations.
     */
    public record RefreshTokenResult(String rawToken, RefreshToken entity) {}
}