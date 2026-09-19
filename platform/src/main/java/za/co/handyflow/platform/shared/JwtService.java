package za.co.handyflow.platform.shared;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

@Slf4j
@Service
public class JwtService {
    private final SecretKey signingKey;
    private final long expirationMs;

    public JwtService(
            @Value("${app.security.jwt.secret}") String secret,
            @Value("${app.security.jwt.expiration-ms}") long expirationMs
    ) {
        // WHY Keys.hmacShaKeyFor instead of just using the string?
        // JJWT requires a properly sized key for HS256 (256 bits minimum).
        // This method ensures the key meets that requirement.
        this.signingKey = Keys.hmacShaKeyFor(
                secret.getBytes(StandardCharsets.UTF_8)
        );
        this.expirationMs = expirationMs;
    }

    // NEW: backs the fix to AuthResponse.expiresIn, which was hardcoded to
    // 86400L in two separate places (AuthService.buildAuthResponse() and
    // UserManagementService.acceptInvitation()) — completely independent
    // of this class's own configured app.security.jwt.expiration-ms.
    // Whatever the real token's expiry actually is, this is the one place
    // that value should be read from, so the two can never silently drift
    // apart again.
    public long getExpirationSeconds() {
        return expirationMs / 1000;
    }

    public String generateToken(UUID userId, UUID tenantId,
                                String email, String firstName,
                                String lastName, Set<String> permissions) {
        return Jwts.builder()
                .subject(userId.toString())
                .claims(Map.of(
                        "tenantId", tenantId.toString(),
                        "email", email,
                        "firstName", firstName != null ? firstName : "",
                        "lastName",  lastName  != null ? lastName  : "",
                        "permissions", permissions
                ))
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + expirationMs))
                .signWith(signingKey)
                .compact();

    }

    public String extractUserId(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    public String extractTenantId(String token) {
        return  extractClaim(token, claims -> claims.get("tenantId", String.class));
    }

    public String extractEmail(String token) {
        return extractClaim(token, claims -> claims.get("email", String.class));
    }

    /** Returns "FirstName LastName" from the JWT, or empty string if absent. */
    public String extractName(String token) {
        String fn = extractClaim(token, claims -> claims.get("firstName", String.class));
        String ln = extractClaim(token, claims -> claims.get("lastName",  String.class));
        fn = fn != null ? fn : "";
        ln = ln != null ? ln : "";
        String full = (fn + " " + ln).trim();
        return full.isEmpty() ? "" : full;
    }

    /**
     * True for an admin-impersonation token (AdminAuthService.
     * generateImpersonationToken — subject "IMPERSONATION", claim
     * "role": "IMPERSONATION"). JwtAuthFilter uses this to tell
     * TenantContext the current request has no real tenant user behind
     * it, so TenantContext.getCurrentUserId() can fail with a clear,
     * intentional message instead of a raw UUID-parse exception — the
     * subject of this token is literally the string "IMPERSONATION",
     * not a user UUID, precisely because no real user is impersonating
     * anyone; it's read-only support access.
     */
    public boolean isImpersonation(String token) {
        String role = extractClaim(token, claims -> claims.get("role", String.class));
        return "IMPERSONATION".equals(role);
    }

    /**
     * FIX (Admin Console gap analysis): this threw a NullPointerException
     * for any token with no "permissions" claim at all — which is exactly
     * the shape of the admin-impersonation JWT AdminAuthService.
     * generateImpersonationToken() issues (it carries "role":"IMPERSONATION"
     * and "readOnly":true, but never a "permissions" claim). Because
     * JwtAuthFilter catches this broadly and just logs + falls through
     * unauthenticated, the failure was silent: the entire "view tenant
     * read-only" impersonation feature — despite a fully-built session
     * table, audit log, and TOTP-gated superadmin login behind it —
     * could never actually reach a business endpoint. This fix only makes
     * the extraction null-safe (empty set instead of throwing); it does
     * NOT yet make impersonation functionally useful, since every business
     * endpoint requires a specific granted authority (e.g.
     * "INVOICE_READ") and an empty set satisfies none of them — that
     * still needs a real design decision (a dedicated read-only authority
     * set, or a distinct authorization path for role=IMPERSONATION) rather
     * than being guessed at here. See PLATFORM-ENGINES-PROGRESS.md.
     */
    @SuppressWarnings("unchecked")
    public Set<String> extractPermissions(String token) {
        return extractClaim(token, claims -> {
            Object raw = claims.get("permissions");
            if (raw == null) {
                return Set.of();
            }
            return Set.copyOf((java.util.List<String>) raw);
        });
    }

    public boolean isTokenValid(String token) {
        try {
            parseClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            log.warn("Invalid JWT token: {}", e.getMessage());
            return false;
        }
    }

    private <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        return claimsResolver.apply(parseClaims(token));
    }

    public Claims extractAllClaims(String token) { return parseClaims(token); }
    private Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}