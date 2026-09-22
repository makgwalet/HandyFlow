package za.co.handyflow.platform.admin.application.internal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import za.co.handyflow.platform.admin.domain.model.AdminAuditLog;
import za.co.handyflow.platform.admin.domain.repository.AdminAuditLogRepository;
import za.co.handyflow.platform.shared.HandyFlowException;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Regression test for the permission-read-only admin action added this
 * session (see PLATFORM-ENGINES-PROGRESS.md, "fix it for me" actions):
 * lets a support engineer review and correct the V287 backfill's
 * heuristic (which permissions are granted to an admin impersonation
 * session) through an audited action instead of raw SQL. Deliberately a
 * plain JdbcTemplate read/patch, not a call into another module — the
 * is_read_only column has no invariants beyond itself, unlike the
 * business-logic-backed actions (resend verification email, regenerate
 * PDF) that turned out to be structurally blocked by admin's module
 * boundary this session.
 */
@ExtendWith(MockitoExtension.class)
class AdminServicePermissionsTest {

    @Mock private AdminAuditLogRepository auditRepo;
    @Mock private JdbcTemplate jdbc;
    @Mock private AdminNotificationService notificationService;
    @Mock private AdminReportingService adminReportingService;

    private AdminService service() {
        return new AdminService(auditRepo, jdbc, notificationService, adminReportingService);
    }

    @Test
    @DisplayName("listPermissions returns whatever the query returns, unmodified")
    void listPermissions_returnsQueryResult() {
        List<Map<String, Object>> rows = List.of(
                Map.of("name", "INVOICE_READ", "description", "View invoices", "is_read_only", true),
                Map.of("name", "INVOICE_DELETE", "description", "Delete invoices", "is_read_only", false));
        when(jdbc.queryForList(any(String.class))).thenReturn(rows);

        List<Map<String, Object>> result = service().listPermissions();

        assertThat(result).isEqualTo(rows);
    }

    @Test
    @DisplayName("setPermissionReadOnly updates the row and writes an audit log entry")
    void setPermissionReadOnly_updatesAndAudits() {
        when(jdbc.update(any(String.class), eq(true), eq("INVOICE_READ"))).thenReturn(1);

        UUID adminId = UUID.randomUUID();
        service().setPermissionReadOnly(adminId, "support@handyflow.co.za", "INVOICE_READ", true, "127.0.0.1");

        ArgumentCaptor<AdminAuditLog> captor = ArgumentCaptor.forClass(AdminAuditLog.class);
        verify(auditRepo).save(captor.capture());
        AdminAuditLog log = captor.getValue();
        assertThat(log.getAction()).isEqualTo("SET_PERMISSION_READ_ONLY");
        assertThat(log.getTargetId()).isEqualTo("INVOICE_READ");
    }

    @Test
    @DisplayName("setPermissionReadOnly throws NOT_FOUND for an unknown permission name, and audits nothing")
    void setPermissionReadOnly_unknownName_throwsAndDoesNotAudit() {
        when(jdbc.update(any(String.class), eq(true), eq("NOT_A_REAL_PERMISSION"))).thenReturn(0);

        assertThatThrownBy(() -> service().setPermissionReadOnly(
                UUID.randomUUID(), "support@handyflow.co.za", "NOT_A_REAL_PERMISSION", true, "127.0.0.1"))
                .isInstanceOf(HandyFlowException.class);

        verify(auditRepo, org.mockito.Mockito.never()).save(any());
    }
}
