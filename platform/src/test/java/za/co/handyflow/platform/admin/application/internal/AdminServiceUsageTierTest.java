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

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminServiceUsageTierTest {

    @Mock private AdminAuditLogRepository auditRepo;
    @Mock private JdbcTemplate jdbc;
    @Mock private AdminNotificationService notificationService;
    @Mock private AdminReportingService adminReportingService;

    private AdminService service() { return new AdminService(auditRepo, jdbc, notificationService, adminReportingService); }

    @Test
    @DisplayName("an unknown tier is refused and nothing is written")
    void unknownTier() {
        assertThatThrownBy(() -> service().setUsageTier(UUID.randomUUID(), "a@x.co", "acme", "GOLD", "Because we said so", null))
                .isInstanceOf(HandyFlowException.class).hasMessageContaining("Unknown tier");
        verify(jdbc, never()).update(any(String.class), any(Object[].class));
    }

    @Test
    @DisplayName("a reason of at least 10 characters is required")
    void reasonRequired() {
        assertThatThrownBy(() -> service().setUsageTier(UUID.randomUUID(), "a@x.co", "acme", "STARTER", "short", null))
                .isInstanceOf(HandyFlowException.class).hasMessageContaining("reason");
        verify(jdbc, never()).update(any(String.class), any(Object[].class));
    }

    @Test
    @DisplayName("setting a tier updates the tenant and audits old and new value with the reason")
    void setsAndAudits() {
        when(jdbc.queryForObject(any(String.class), eq(String.class), any(Object[].class))).thenReturn(UUID.randomUUID().toString());
        service().setUsageTier(UUID.randomUUID(), "a@x.co", "acme", "starter", "Approved Starter tier", "1.2.3.4");
        verify(jdbc).update(any(String.class), eq("STARTER"), eq("acme"));
        ArgumentCaptor<AdminAuditLog> log = ArgumentCaptor.forClass(AdminAuditLog.class);
        verify(auditRepo).save(log.capture());
        assertThat(log.getValue().getAction()).isEqualTo("SET_USAGE_TIER");
    }

    @Test
    @DisplayName("a blank tier clears it")
    void clears() {
        when(jdbc.queryForObject(any(String.class), eq(String.class), any(Object[].class))).thenReturn(UUID.randomUUID().toString());
        service().setUsageTier(UUID.randomUUID(), "a@x.co", "acme", " ", "Removing limits for now", null);
        verify(jdbc).update(any(String.class), eq((Object) null), eq("acme"));
    }
}
