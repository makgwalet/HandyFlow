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

    @Test
    @DisplayName("isImpersonation is true for an impersonation-shaped token")
    void isImpersonation_impersonationToken_returnsTrue() {
        assertThat(jwtService.isImpersonation(tokenWithoutPermissionsClaim())).isTrue();
    }

    @Test
    @DisplayName("isImpersonation is false for a normal user token")
    void isImpersonation_normalToken_returnsFalse() {
        String token = jwtService.generateToken(
                UUID.randomUUID(), UUID.randomUUID(), "user@example.com", "Jane", "Doe",
                Set.of("INVOICE_READ"));

        assertThat(jwtService.isImpersonation(token)).isFalse();
    }

    @Test
    @DisplayName("a user with hundreds of permissions gets a token that fits in a request header")
    void largePermissionSet_staysSmallAndRoundTrips() {
        Set<String> many = new java.util.HashSet<>();
        for (String m : new String[]{"CLINIC", "HR", "ACCOUNTING", "INVENTORY", "CRM", "INVOICE"})
            for (String g : new String[]{"PATIENT", "NOTES", "CONSULTATION", "NURSE", "PRESCRIPTION", "RESULT", "BILL", "CLAIM", "APPOINTMENT", "CONTENT", "TASK", "GROWTH"})
                for (String v : new String[]{"READ", "CREATE", "UPDATE", "DELETE", "SIGN", "VOID", "EXPORT", "MANAGE"})
                    many.add(m + "_" + g + "_" + v);
        String token = jwtService.generateToken(UUID.randomUUID(), UUID.randomUUID(), "user@example.com", "Jane", "Doe", many);

        assertThat(token.length()).isLessThan(4000);
        assertThat(jwtService.extractPermissions(token)).isEqualTo(many);
    }

    @Test
    @DisplayName("a token issued before the packed claim still yields its plain permission list")
    void oldPlainClaim_stillRead() {
        SecretKey key = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
        String old = Jwts.builder()
                .subject(UUID.randomUUID().toString())
                .claim("tenantId", UUID.randomUUID().toString())
                .claim("permissions", java.util.List.of("INVOICE_READ", "CLINIC_READ"))
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 900_000L))
                .signWith(key)
                .compact();

        assertThat(jwtService.extractPermissions(old)).containsExactlyInAnyOrder("INVOICE_READ", "CLINIC_READ");
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
