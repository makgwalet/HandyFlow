package za.co.handyflow.platform.admin.application.internal;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import za.co.handyflow.platform.admin.domain.model.AdminAuditLog;
import za.co.handyflow.platform.admin.domain.model.AdminImpersonationSession;
import za.co.handyflow.platform.admin.domain.repository.AdminAuditLogRepository;
import za.co.handyflow.platform.admin.domain.repository.AdminImpersonationSessionRepository;
import za.co.handyflow.platform.admin.domain.repository.AdminUserRepository;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * Regression test for the admin-impersonation "can't actually see
 * anything" gap (see PLATFORM-ENGINES-PROGRESS.md): the impersonation
 * token now carries a real "permissions" claim built from
 * {@code SELECT name FROM permissions WHERE is_read_only = true} (V287),
 * instead of no "permissions" claim at all.
 */
@ExtendWith(MockitoExtension.class)
class AdminAuthServiceImpersonationTest {

    private static final String SECRET = "test-secret-key-must-be-at-least-256-bits-long-for-hs256!!";

    @Mock
    private AdminUserRepository adminUserRepo;
    @Mock
    private AdminAuditLogRepository auditRepo;
    @Mock
    private AdminImpersonationSessionRepository impersonationRepo;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private JdbcTemplate jdbc;

    @InjectMocks
    private AdminAuthService service;

    @Test
    @DisplayName("impersonation token carries the real read-only permission names, not an empty/absent claim")
    void impersonationTokenCarriesReadOnlyPermissions() {
        ReflectionTestUtils.setField(service, "jwtSecret", SECRET);
        when(jdbc.queryForList(any(String.class), eq(String.class)))
                .thenReturn(List.of("INVOICE_READ", "USER_READ", "REPORT_VIEW"));
        when(impersonationRepo.save(any(AdminImpersonationSession.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(auditRepo.save(any(AdminAuditLog.class))).thenAnswer(inv -> inv.getArgument(0));

        UUID adminUserId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();

        String token = service.impersonateTenant(
                adminUserId, "support@handyflow.co.za", tenantId, "fastprint",
                "customer requested help with an invoice", "127.0.0.1");

        SecretKey key = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
        var claims = Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();

        @SuppressWarnings("unchecked")
        List<String> permissions = (List<String>) claims.get("permissions");
        assertThat(permissions).containsExactlyInAnyOrder("INVOICE_READ", "USER_READ", "REPORT_VIEW");
        assertThat(claims.get("role")).isEqualTo("IMPERSONATION");
        assertThat(claims.getSubject()).isEqualTo("IMPERSONATION");
    }
}
