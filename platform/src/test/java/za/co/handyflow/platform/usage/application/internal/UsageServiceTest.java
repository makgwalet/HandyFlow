package za.co.handyflow.platform.usage.application.internal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import za.co.handyflow.platform.shared.TenantId;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class UsageServiceTest {

    private final TenantId tenant = TenantId.of(UUID.randomUUID());
    JdbcTemplate jdbc;
    UsageService service;

    @BeforeEach
    void setUp() {
        jdbc = mock(JdbcTemplate.class);
        service = new UsageService(jdbc);
    }

    @SuppressWarnings("unchecked")
    private <T> void answer(String sqlStart, Class<T> type, T value) {
        when(jdbc.queryForList(org.mockito.ArgumentMatchers.startsWith(sqlStart), eq(type), any(Object[].class)))
                .thenReturn(value == null ? List.of() : List.of(value));
    }

    @Test
    void withoutATierThereAreNoLimitsButTheCountsAreStillShown() {
        answer("SELECT usage_tier", String.class, null);
        answer("SELECT count(*) FROM usage_events", Long.class, 12L);
        answer("SELECT count(*) FROM users", Long.class, 3L);
        answer("SELECT count(*) FROM user_invitations", Long.class, 1L);

        var s = service.snapshot(tenant, LocalDate.of(2026, 11, 5));

        assertThat(s.period()).isEqualTo("2026-11");
        assertThat(s.tier()).isNull();
        assertThat(s.invoices().used()).isEqualTo(12);
        assertThat(s.invoices().limit()).isNull();
        assertThat(s.invoices().level()).isEqualTo(UsageRules.NO_LIMIT);
        assertThat(s.seats().used()).isEqualTo(4);   // 3 active + 1 invitation holding a seat
        assertThat(s.activeUsers()).isEqualTo(3);
        assertThat(s.pendingInvitations()).isEqualTo(1);
    }

    @Test
    void aStarterBusinessAtFortyOfFiftyInvoicesIsNearItsLimit() {
        answer("SELECT usage_tier", String.class, "STARTER");
        answer("SELECT limit_value", Integer.class, null);
        when(jdbc.queryForList(org.mockito.ArgumentMatchers.startsWith("SELECT limit_value"), eq(Integer.class), eq("STARTER"), eq("INVOICES_ISSUED"))).thenReturn(List.of(50));
        when(jdbc.queryForList(org.mockito.ArgumentMatchers.startsWith("SELECT limit_value"), eq(Integer.class), eq("STARTER"), eq("SEATS"))).thenReturn(List.of(5));
        answer("SELECT bool_or", Boolean.class, true);
        answer("SELECT count(*) FROM usage_events", Long.class, 40L);
        answer("SELECT count(*) FROM users", Long.class, 5L);
        answer("SELECT count(*) FROM user_invitations", Long.class, 0L);

        var s = service.snapshot(tenant, LocalDate.of(2026, 11, 5));

        assertThat(s.limitsProvisional()).isTrue();
        assertThat(s.invoices().level()).isEqualTo(UsageRules.NEAR);
        assertThat(s.invoices().percent()).isEqualTo(80);
        assertThat(s.seats().level()).isEqualTo(UsageRules.AT_LIMIT);
    }
}
