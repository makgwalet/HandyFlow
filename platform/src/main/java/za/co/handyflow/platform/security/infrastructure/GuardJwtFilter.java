// security/infrastructure/GuardJwtFilter.java

package za.co.handyflow.platform.security.infrastructure;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import za.co.handyflow.platform.security.application.internal.GuardAuthService;
import za.co.handyflow.platform.security.domain.model.GuardToken;
import za.co.handyflow.platform.shared.TenantContext;
import za.co.handyflow.platform.shared.TenantId;
import org.springframework.security.core.context.SecurityContextHolder;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

/**
 * GuardJwtFilter — validates guard session tokens on /api/v1/guard/** routes.
 *
 * WHY a separate filter from JwtAuthFilter?
 * Guard tokens carry different claims (guardId, role: GUARD, authorities list)
 * vs tenant user tokens (email, tenantId, permissions array).  Rather than
 * making JwtAuthFilter understand both shapes, a dedicated filter handles the
 * guard path only — cleaner separation and no risk of a guard token accidentally
 * granting tenant-level access through a shared filter.
 *
 * What this filter does:
 *   1. Skips any request that is not /api/v1/guard/**
 *   2. Extracts Bearer token from Authorization header
 *   3. Verifies JWT signature against app.jwt.secret
 *   4. Checks role = "GUARD" claim (rejects tenant user tokens on guard endpoints)
 *   5. Validates jti against security_guard_tokens table (revocation check)
 *   6. Sets SecurityContext with guard authorities
 *   7. Populates TenantContext so downstream code can call
 *      TenantContext.getTenantIdAsObject() and getCurrentUserId() normally
 *
 * Filter ordering: registered BEFORE JwtAuthFilter in Spring Security config
 * (or at a separate position — guard endpoints don't need JwtAuthFilter to run).
 *
 * Spring Security config change needed:
 *   .requestMatchers("/api/v1/guard/**").hasAuthority("SECURITY_GUARD")
 *   // and register this filter:
 *   http.addFilterBefore(guardJwtFilter, JwtAuthFilter.class);
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GuardJwtFilter extends OncePerRequestFilter {

    private final GuardAuthService guardAuthService;

    @Value("${app.security.jwt.secret}")
    private String jwtSecret;

    private static final String GUARD_PATH_PREFIX = "/api/v1/guard/";

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        // Only intercept guard-specific paths — all other paths are handled by JwtAuthFilter
        return !request.getRequestURI().startsWith(GUARD_PATH_PREFIX);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest  request,
                                    HttpServletResponse response,
                                    FilterChain         chain)
            throws ServletException, IOException {

        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            sendUnauthorized(response, "Missing or invalid Authorization header");
            return;
        }

        String token = authHeader.substring(7);
        Claims claims;
        try {
            claims = Jwts.parser()
                    .verifyWith(Keys.hmacShaKeyFor(
                            jwtSecret.getBytes(StandardCharsets.UTF_8)))
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (Exception e) {
            log.debug("[GuardJwtFilter] Invalid token: {}", e.getMessage());
            sendUnauthorized(response, "Invalid or expired token");
            return;
        }

        // Reject non-guard tokens (tenant user tokens must not work on guard endpoints)
        String role = claims.get("role", String.class);
        if (!"GUARD".equals(role)) {
            sendUnauthorized(response, "This endpoint requires a guard session token");
            return;
        }

        // Revocation check — this is the DB lookup that stateless JWTs skip
        String jtiStr = claims.getId();
        if (jtiStr == null) {
            sendUnauthorized(response, "Token missing jti claim");
            return;
        }

        UUID jti = UUID.fromString(jtiStr);
        java.util.Optional<GuardToken> guardToken = guardAuthService.validateToken(jti);
        if (guardToken.isEmpty()) {
            log.info("[GuardJwtFilter] Revoked or expired token jti={}", jti);
            sendUnauthorized(response, "Session has been revoked or expired");
            return;
        }

        // Extract claims
        String guardIdStr = claims.getSubject();
        String tenantIdStr = claims.get("tenantId", String.class);

        @SuppressWarnings("unchecked")
        List<String> authorityClaims = claims.get("authorities", List.class);

        List<SimpleGrantedAuthority> grantedAuthorities = (authorityClaims != null)
                ? authorityClaims.stream()
                .map(SimpleGrantedAuthority::new)
                .toList()
                : List.of(new SimpleGrantedAuthority("SECURITY_GUARD"));

        // Set Spring Security context
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(guardIdStr, null, grantedAuthorities);
        SecurityContextHolder.getContext().setAuthentication(authentication);

        // Store tenant + guard identity in authentication details so TenantContext
        // can read them via the same mechanism as JwtAuthFilter.
        // TenantContext.getTenantIdAsObject() and getCurrentUserId() read from
        // the SecurityContext principal/details — match whatever your JwtAuthFilter sets.
        java.util.Map<String, Object> details = new java.util.HashMap<>();
        details.put("tenantId", tenantIdStr);
        details.put("userId",   guardIdStr);
        authentication.setDetails(details);

        chain.doFilter(request, response);

        // Clear after request
        SecurityContextHolder.clearContext();
    }

    private void sendUnauthorized(HttpServletResponse response, String message)
            throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json");
        response.getWriter().write(
                "{\"success\":false,\"message\":\"" + message + "\"}");
    }
}