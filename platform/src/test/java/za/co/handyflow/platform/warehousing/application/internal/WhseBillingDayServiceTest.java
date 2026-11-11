package za.co.handyflow.platform.warehousing.application.internal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import za.co.handyflow.platform.shared.TenantId;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class WhseBillingDayServiceTest {

    private JdbcTemplate jdbc;
    private WhseBillingDayService service;
    private final UUID tenant = UUID.randomUUID(), actor = UUID.randomUUID(), client = UUID.randomUUID();
    private final LocalDate today = LocalDate.of(2026, 11, 11);

    @BeforeEach
    void setUp() {
        jdbc = mock(JdbcTemplate.class);
        service = new WhseBillingDayService(jdbc);
    }

    private TenantId t() { return TenantId.of(tenant); }

    @Test @DisplayName("an unidentified user cannot change a rule")
    void needsActor() {
        assertThrows(AccessDeniedException.class, () -> service.addRule(t(), null, "WEEKDAYS_ONLY", today, null, null, today));
        verify(jdbc, never()).update(anyString(), any(Object[].class));
    }

    @Test @DisplayName("a rule starting in the past, or an unknown rule, is refused before anything is written")
    void refusals() {
        assertThrows(IllegalArgumentException.class, () -> service.addRule(t(), null, "WEEKDAYS_ONLY", today.minusDays(1), null, actor, today));
        assertThrows(IllegalArgumentException.class, () -> service.addRule(t(), null, "SUNDAYS", today, null, actor, today));
        verify(jdbc, never()).update(anyString(), any(Object[].class));
    }

    @Test @DisplayName("a rule for a client that does not exist is refused")
    void unknownClient() {
        when(jdbc.queryForObject(contains("FROM whse_clients"), eq(Integer.class), any(Object[].class))).thenReturn(0);
        assertThrows(IllegalArgumentException.class, () -> service.addRule(t(), client, "WEEKDAYS_ONLY", today, null, actor, today));
    }

    @Test @DisplayName("a good rule is inserted and audited")
    void saves() {
        when(jdbc.queryForObject(contains("FROM whse_clients"), eq(Integer.class), any(Object[].class))).thenReturn(1);
        assertNotNull(service.addRule(t(), client, "WEEKDAYS_ONLY", today.plusDays(1), " contract 14 ", actor, today));
        verify(jdbc).update(contains("INSERT INTO whse_billing_day_rules"), any(Object[].class));
        verify(jdbc).update(contains("WHSE_BILLABLE_DAY_RULE_SET"), any(Object[].class));
    }

    @Test @DisplayName("a holiday needs a date and a name")
    void holidays() {
        assertThrows(IllegalArgumentException.class, () -> service.addHoliday(t(), null, "Day", actor));
        assertThrows(IllegalArgumentException.class, () -> service.addHoliday(t(), today, " ", actor));
        service.addHoliday(t(), today, "Day", actor);
        verify(jdbc).update(contains("INSERT INTO whse_billing_holidays"), any(Object[].class));
    }

    @Test @DisplayName("an unknown minimum-charge basis is refused before anything is written")
    void badBasis() {
        assertThrows(IllegalArgumentException.class, () -> service.addRule(t(), null, "WEEKDAYS_ONLY", "weekly", today, null, actor, today));
        verify(jdbc, never()).update(anyString(), any(Object[].class));
    }

    @Test @DisplayName("the minimum basis is stored with the rule")
    void storesBasis() {
        service.addRule(t(), null, "WEEKDAYS_ONLY", "BILLABLE_DAYS", today, null, actor, today);
        verify(jdbc).update(contains("minimum_basis"), any(Object[].class));
    }

    @Test @DisplayName("a rule saved with no basis stores none and is marked DEFAULT; one with a basis is marked EXPLICIT")
    void storesBasisSource() {
        org.mockito.ArgumentCaptor<Object[]> args = org.mockito.ArgumentCaptor.forClass(Object[].class);
        service.addRule(t(), null, "WEEKDAYS_ONLY", null, today, null, actor, today);
        verify(jdbc).update(contains("INSERT INTO whse_billing_day_rules"), args.capture());
        assertNull(args.getValue()[4]);
        assertEquals("DEFAULT", args.getValue()[5]);

        reset(jdbc);
        service.addRule(t(), null, "WEEKDAYS_ONLY", "CALENDAR_DAYS", today, null, actor, today);
        verify(jdbc).update(contains("INSERT INTO whse_billing_day_rules"), args.capture());
        assertEquals("CALENDAR_DAYS", args.getValue()[4]);
        assertEquals("EXPLICIT", args.getValue()[5]);
    }

    @Test @DisplayName("the review list asks for the rules in force today and leaves nothing out because of another tenant")
    @SuppressWarnings({"unchecked", "rawtypes"})
    void reviewListQuery() {
        when(jdbc.query(anyString(), any(org.springframework.jdbc.core.RowMapper.class), any(Object[].class))).thenReturn(List.of());
        assertTrue(service.reviewList(t(), today).isEmpty());
        org.mockito.ArgumentCaptor<String> sql = org.mockito.ArgumentCaptor.forClass(String.class);
        org.mockito.ArgumentCaptor<Object[]> args = org.mockito.ArgumentCaptor.forClass(Object[].class);
        verify(jdbc).query(sql.capture(), any(org.springframework.jdbc.core.RowMapper.class), args.capture());
        assertTrue(sql.getValue().contains("minimum_basis_source = 'LEGACY'"));
        assertTrue(sql.getValue().contains("day_rule <> 'ALL_DAYS'"));
        assertEquals(tenant, args.getValue()[0]);
    }

    @Test @DisplayName("the South African holidays are added without overwriting ones already entered, and only for an identified user")
    void saHolidays() {
        assertThrows(AccessDeniedException.class, () -> service.addSouthAfricanHolidays(t(), 2026, null));
        when(jdbc.update(contains("ON CONFLICT (tenant_id, holiday_date) DO NOTHING"), any(Object[].class))).thenReturn(1);
        assertEquals(13, service.addSouthAfricanHolidays(t(), 2026, actor));
        verify(jdbc, times(13)).update(contains("DO NOTHING"), any(Object[].class));
        assertThrows(IllegalArgumentException.class, () -> service.addSouthAfricanHolidays(t(), 1900, actor));
    }
}
