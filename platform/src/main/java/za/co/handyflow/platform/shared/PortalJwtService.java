package za.co.handyflow.platform.shared;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;
import java.util.function.Function;

/**
 * Closes the "client portal" gap's auth layer. Mirrors JwtService's
 * exact library and pattern (io.jsonwebtoken / JJWT, HMAC signing key
 * via Keys.hmacShaKeyFor) — confirmed directly by reading the real
 * JwtService.java, not guessed at.
 * <p>
 * Deliberately a SEPARATE signing secret (app.security.portal-jwt.secret)
 * from staff/admin tokens (app.security.jwt.secret) — this diverges from
 * how AdminAuthService actually works today (it reuses the staff secret
 * even for a high-privilege domain), but for a real reason: staff and
 * admin are internal, trusted employees sharing one trust boundary; the
 * portal is external, untrusted parties on a fundamentally different
 * one. A stolen or misused portal token should fail signature
 * verification outright against staff/admin endpoints, not merely be
 * "not recognized" and fall through.
 * <p>
 * Claims are deliberately minimal — just the portal user's identity
 * (subject + email). NOT tenant-scoped, NOT client-scoped: which
 * client/tenant a request is actually about is resolved per-endpoint
 * against AccPortalAccessGrantRepository, never baked into the token.
 * This means revoking a grant takes effect on the client's very next
 * request, not on token expiry — the token only proves "who is asking",
 * never "what they're allowed to see".
 */
@Slf4j
@Service
public class PortalJwtService {

    private final SecretKey signingKey;
    private final long expirationMs;

    public PortalJwtService(
            @Value("${app.security.portal-jwt.secret}") String secret,
            @Value("${app.security.portal-jwt.expiration-ms:604800000}") long expirationMs
    ) {
        // 604800000ms = 7 days default — portal sessions are long-lived
        // by design (a client checking their fee notes monthly
        // shouldn't need to re-login every day), unlike staff tokens.
        this.signingKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expirationMs = expirationMs;
    }

    public String generateToken(UUID portalUserId, String email) {
        return Jwts.builder()
                .subject(portalUserId.toString())
                .claim("email", email)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + expirationMs))
                .signWith(signingKey)
                .compact();
    }

    public boolean isTokenValid(String token) {
        try {
            parseClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            log.warn("Invalid portal JWT: {}", e.getMessage());
            return false;
        }
    }

    public UUID extractPortalUserId(String token) {
        return UUID.fromString(extractClaim(token, Claims::getSubject));
    }

    public String extractEmail(String token) {
        return extractClaim(token, claims -> claims.get("email", String.class));
    }

    private <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        return claimsResolver.apply(parseClaims(token));
    }

    private Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}