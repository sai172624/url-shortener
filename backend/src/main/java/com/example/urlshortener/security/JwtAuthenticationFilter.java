package com.example.urlshortener.security;

import com.example.urlshortener.service.JwtService;
import com.example.urlshortener.service.RefreshTokenService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final UserDetailsService userDetailsService;
    private final RefreshTokenService refreshTokenService;

    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                     @NonNull HttpServletResponse response,
                                     @NonNull FilterChain filterChain) throws ServletException, IOException {

        try {
            String jwt = extractToken(request);

            if (jwt != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                // Validate token
                var jws = jwtService.validateToken(jwt);
                Long userId = Long.valueOf(jws.getPayload().getSubject());

                // Load user details
                UserPrincipal userPrincipal = (UserPrincipal) userDetailsService.loadUserByUsername(jws.getPayload().get("email", String.class));

                // Additional check: ensure user is still active
                if (userPrincipal.isEnabled()) {
                    UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                            userPrincipal, null, userPrincipal.getAuthorities());
                    authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(authToken);
                }
            }
        } catch (Exception e) {
            // Don't block the request - let Spring Security handle auth failures
            log.debug("JWT validation failed: {}", e.getMessage());
            SecurityContextHolder.clearContext();
        }

        filterChain.doFilter(request, response);
    }

    private String extractToken(HttpServletRequest request) {
        String bearer = request.getHeader(AUTHORIZATION_HEADER);
        if (StringUtils.hasText(bearer) && bearer.startsWith(BEARER_PREFIX)) {
            return bearer.substring(BEARER_PREFIX.length());
        }
        return null;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();

        // Skip JWT filter for public endpoints
        return path.equals("/api/v1/auth/register") ||
               path.equals("/api/v1/auth/login") ||
               path.equals("/api/v1/auth/refresh") ||
               path.startsWith("/actuator/") ||
               // Public redirect - exact match or pattern
               path.matches("/api/v1/[a-zA-Z0-9]{1,16}") ||
               // Root level redirect (if configured differently)
               path.matches("/[a-zA-Z0-9]{1,16}");
    }
}