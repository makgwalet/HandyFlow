package za.co.handyflow.platform.warehousing.application.internal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import za.co.handyflow.platform.shared.TenantId;

import java.time.LocalDate;
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
}
