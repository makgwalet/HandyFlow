package za.co.handyflow.platform.shared;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regression test for the NPE found while investigating the Admin Console
 * impersonation flow (see JwtService.extractPermissions Javadoc and
 * PLATFORM-ENGINES-PROGRESS.md): a token with no "permissions" claim at
 * all — exactly the shape AdminAuthService.generateImpersonationToken()
 * issues — used to throw a NullPointerException instead of being treated
 * as "no permissions granted".
 */
class JwtServiceTest {

    private static final String SECRET =
            "test-secret-key-must-be-at-least-256-bits-long-for-hs256!!";

    private final JwtService jwtService = new JwtService(SECRET, 3_600_000L);

    @Test
    @DisplayName("extractPermissions returns an empty set (not NPE) for a token with no permissions claim")
    void extractPermissions_noClaimAtAll_returnsEmptySet() {
        String tokenWithoutPermissionsClaim = tokenWithoutPermissionsClaim();

        Set<String> permissions = jwtService.extractPermissions(tokenWithoutPermissionsClaim);

        assertThat(permissions).isEmpty();
    }

    @Test
    @DisplayName("extractPermissions still returns the real set for a normal token")
    void extractPermissions_normalToken_returnsConfiguredPermissions() {
        String token = jwtService.generateToken(
                UUID.randomUUID(), UUID.randomUUID(), "user@example.com", "Jane", "Doe",
                Set.of("INVOICE_READ", "INVOICE_CREATE"));

        Set<String> permissions = jwtService.extractPermissions(token);

        assertThat(permissions).containsExactlyInAnyOrder("INVOICE_READ", "INVOICE_CREATE");
    }

    /** Same claim shape as AdminAuthService.generateImpersonationToken() — no "permissions" claim. */
    private String tokenWithoutPermissionsClaim() {
        SecretKey key = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
        return Jwts.builder()
                .subject("IMPERSONATION")
                .claim("tenantId", UUID.randomUUID().toString())
                .claim("role", "IMPERSONATION")
                .claim("readOnly", true)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 900_000L))
                .signWith(key)
                .compact();
    }
}
