package za.co.handyflow.platform.shared;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BusinessCalendarOptionalTest {

    @Test
    void withoutACalendarTheDefaultZoneIsUsedNeverTheServers() {
        java.util.TimeZone before = java.util.TimeZone.getDefault();
        try {
            java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("Pacific/Auckland")); // a server zone that is not South African
            TenantId t = TenantId.generate();
            assertEquals(BusinessTime.DEFAULT_ZONE, BusinessCalendar.zoneOf(null, t));
            assertEquals(BusinessTime.today(BusinessTime.DEFAULT_ZONE, Instant.now()), BusinessCalendar.todayOf(null, t));
        } finally { java.util.TimeZone.setDefault(before); }
    }

    @Test
    void withACalendarTheCompanyZoneAndDayAreUsed() {
        JdbcTemplate jdbc = new JdbcTemplate() {
            @Override public <T> List<T> queryForList(String sql, Class<T> type, Object... args) { List<T> l = new ArrayList<>(); l.add(type.cast("Pacific/Auckland")); return l; }
        };
        Clock fixed = new Clock() {
            @Override public ZoneId getZone() { return ZoneOffset.UTC; }
            @Override public Clock withZone(ZoneId z) { return this; }
            @Override public Instant instant() { return Instant.parse("2026-11-22T20:00:00Z"); } // already 23 Nov in Auckland
        };
        BusinessCalendar cal = new BusinessCalendar(jdbc, fixed);
        TenantId t = TenantId.of(UUID.randomUUID());
        assertEquals(ZoneId.of("Pacific/Auckland"), BusinessCalendar.zoneOf(cal, t));
        assertEquals(LocalDate.of(2026, 11, 23), BusinessCalendar.todayOf(cal, t));
    }
}
