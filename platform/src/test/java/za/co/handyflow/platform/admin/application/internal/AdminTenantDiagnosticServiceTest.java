package za.co.handyflow.platform.admin.application.internal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import za.co.handyflow.platform.admin.dto.TenantDiagnosticCheck;
import za.co.handyflow.platform.shared.HandyFlowException;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

/**
 * Pure unit tests for AdminTenantDiagnosticService — no Spring context,
 * matching this codebase's established convention. Stubs JdbcTemplate
 * directly since this module's own established pattern (see AdminService)
 * queries via JdbcTemplate rather than JPA repositories.
 */
@ExtendWith(MockitoExtension.class)
class AdminTenantDiagnosticServiceTest {

    @Mock
    private JdbcTemplate jdbc;

    @InjectMocks
    private AdminTenantDiagnosticService service;

    private final UUID tenantId = UUID.randomUUID();

    @Test
    @DisplayName("healthy tenant: all checks pass")
    void healthyTenant_allChecksPass() {
        Map<String, Object> tenantRow = Map.of(
                "id", tenantId, "status", "ACTIVE", "document_code", "FPS",
                "vat_number", "4123456789", "logo_url", "data:image/png;base64,abc");
        when(jdbc.queryForMap(anyString(), any(Object.class), any(Object.class))).thenReturn(tenantRow);
        when(jdbc.queryForObject(anyString(), eq(Integer.class), eq(tenantId)))
                .thenReturn(3)   // users
                .thenReturn(2)   // active modules
                .thenReturn(1);  // signature enabled

        List<TenantDiagnosticCheck> checks = service.getDiagnostics("fastprint");

        assertThat(checks).allMatch(TenantDiagnosticCheck::pass);
        assertThat(checks).extracting(TenantDiagnosticCheck::key)
                .contains("TENANT_STATUS", "HAS_USERS", "HAS_ACTIVE_MODULES",
                        "DOCUMENT_CODE", "EMAIL_SIGNATURE", "VAT_NUMBER", "LOGO");
    }

    @Test
    @DisplayName("unconfigured opt-in items fail as INFORMATIONAL, not CRITICAL")
    void unconfiguredOptionalItems_areInformationalNotCritical() {
        Map<String, Object> tenantRow = new java.util.HashMap<>();
        tenantRow.put("id", tenantId);
        tenantRow.put("status", "TRIAL");
        tenantRow.put("document_code", null);
        tenantRow.put("vat_number", null);
        tenantRow.put("logo_url", null);
        when(jdbc.queryForMap(anyString(), any(Object.class), any(Object.class))).thenReturn(tenantRow);
        when(jdbc.queryForObject(anyString(), eq(Integer.class), eq(tenantId)))
                .thenReturn(1).thenReturn(1).thenReturn(0);

        List<TenantDiagnosticCheck> checks = service.getDiagnostics("newco");

        TenantDiagnosticCheck docCode = findCheck(checks, "DOCUMENT_CODE");
        assertThat(docCode.pass()).isFalse();
        assertThat(docCode.severity()).isEqualTo(TenantDiagnosticCheck.Severity.INFORMATIONAL);

        TenantDiagnosticCheck signature = findCheck(checks, "EMAIL_SIGNATURE");
        assertThat(signature.pass()).isFalse();
        assertThat(signature.severity()).isEqualTo(TenantDiagnosticCheck.Severity.INFORMATIONAL);
    }

    @Test
    @DisplayName("suspended tenant fails TENANT_STATUS as CRITICAL")
    void suspendedTenant_statusCheckFailsAsCritical() {
        Map<String, Object> tenantRow = Map.of(
                "id", tenantId, "status", "SUSPENDED", "document_code", "FPS",
                "vat_number", "4123456789", "logo_url", "data:x");
        when(jdbc.queryForMap(anyString(), any(Object.class), any(Object.class))).thenReturn(tenantRow);
        when(jdbc.queryForObject(anyString(), eq(Integer.class), eq(tenantId)))
                .thenReturn(1).thenReturn(1).thenReturn(1);

        List<TenantDiagnosticCheck> checks = service.getDiagnostics("suspendedco");

        TenantDiagnosticCheck statusCheck = findCheck(checks, "TENANT_STATUS");
        assertThat(statusCheck.pass()).isFalse();
        assertThat(statusCheck.severity()).isEqualTo(TenantDiagnosticCheck.Severity.CRITICAL);
    }

    @Test
    @DisplayName("unknown tenant throws a 404 HandyFlowException, not an unhandled exception")
    void unknownTenant_throwsNotFound() {
        when(jdbc.queryForMap(anyString(), any(Object.class), any(Object.class)))
                .thenThrow(new org.springframework.dao.EmptyResultDataAccessException(1));

        assertThatThrownBy(() -> service.getDiagnostics("does-not-exist"))
                .isInstanceOf(HandyFlowException.class);
    }

    private TenantDiagnosticCheck findCheck(List<TenantDiagnosticCheck> checks, String key) {
        return checks.stream().filter(c -> c.key().equals(key)).findFirst()
                .orElseThrow(() -> new AssertionError("No check with key " + key));
    }
}
