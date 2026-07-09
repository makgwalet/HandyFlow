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

    @SuppressWarnings("unchecked")
    public Set<String> extractPermissions(String token) {
        return extractClaim(token, claims ->
                Set.copyOf((java.util.List<String>) claims.get("permissions")));
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