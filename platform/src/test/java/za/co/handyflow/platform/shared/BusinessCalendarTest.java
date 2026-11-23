package za.co.handyflow.platform.shared;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class BusinessCalendarTest {

    /** Answers the time-zone lookup from a map and counts the calls; can be told to fail. */
    static class FakeJdbc extends JdbcTemplate {
        final Map<UUID, String> zones = new HashMap<>();
        int calls;
        boolean failing;
        @Override
        public <T> List<T> queryForList(String sql, Class<T> type, Object... args) {
            calls++;
            if (failing) throw new DataAccessResourceFailureException("down");
            assertTrue(sql.contains("FROM tenants WHERE id = ?"), sql);
            String z = zones.get((UUID) args[0]);
            List<T> out = new ArrayList<>();
            if (z != null) out.add(type.cast(z));
            return out;
        }
    }

    /** A clock the test can move. */
    static class Movable extends Clock {
        Instant now = Instant.parse("2026-11-22T07:00:00Z");
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId z) { return this; }
        @Override public Instant instant() { return now; }
    }

    private final FakeJdbc jdbc = new FakeJdbc();
    private final Movable clock = new Movable();
    private final BusinessCalendar cal = new BusinessCalendar(jdbc, clock);
    private final UUID a = UUID.randomUUID(), b = UUID.randomUUID();

    @Test
    void eachCompanyGetsItsOwnZone() {
        jdbc.zones.put(a, "Africa/Johannesburg");
        jdbc.zones.put(b, "Africa/Windhoek");
        assertEquals(ZoneId.of("Africa/Johannesburg"), cal.zone(a));
        assertEquals(ZoneId.of("Africa/Windhoek"), cal.zone(b));
    }

    @Test
    void aMissingOrUnknownZoneIsSouthAfrica() {
        assertEquals(BusinessTime.DEFAULT_ZONE, cal.zone(UUID.randomUUID()));
        jdbc.zones.put(a, "Mars/Olympus");
        assertEquals(BusinessTime.DEFAULT_ZONE, cal.zone(a));
    }

    @Test
    void theLookupIsRememberedForFiveMinutesThenRepeated() {
        jdbc.zones.put(a, "Africa/Windhoek");
        cal.zone(a); cal.zone(a); cal.zone(a);
        assertEquals(1, jdbc.calls);
        clock.now = clock.now.plus(BusinessCalendar.CACHE_FOR).minusSeconds(1);
        cal.zone(a);
        assertEquals(1, jdbc.calls);
        clock.now = clock.now.plusSeconds(2);
        jdbc.zones.put(a, "UTC");
        assertEquals(ZoneId.of("UTC"), cal.zone(a));
        assertEquals(2, jdbc.calls);
    }

    @Test
    void evictingForgetsAChangedZone() {
        jdbc.zones.put(a, "Africa/Windhoek");
        assertEquals(ZoneId.of("Africa/Windhoek"), cal.zone(a));
        jdbc.zones.put(a, "UTC");
        assertEquals(ZoneId.of("Africa/Windhoek"), cal.zone(a));
        cal.evict(a);
        assertEquals(ZoneId.of("UTC"), cal.zone(a));
    }

    @Test
    void aFailedLookupGivesTheDefaultAndIsNotRemembered() {
        jdbc.zones.put(a, "Africa/Windhoek");
        jdbc.failing = true;
        assertEquals(BusinessTime.DEFAULT_ZONE, cal.zone(a));
        jdbc.failing = false;
        assertEquals(ZoneId.of("Africa/Windhoek"), cal.zone(a));
    }

    @Test
    void todayAndRangesUseTheCompanyZoneAndTheInjectedClock() {
        jdbc.zones.put(a, "Africa/Johannesburg");
        TenantId t = TenantId.of(a);
        clock.now = Instant.parse("2026-11-22T22:30:00Z"); // 00:30 SAST on the 23rd
        assertEquals(LocalDate.of(2026, 11, 23), cal.today(t));
        clock.now = Instant.parse("2026-11-22T21:59:00Z"); // 23:59 SAST on the 22nd
        assertEquals(LocalDate.of(2026, 11, 22), cal.today(t));
        assertEquals(Instant.parse("2026-10-31T22:00:00Z"), cal.monthRange(t, YearMonth.of(2026, 11))[0]);
        assertEquals(Instant.parse("2026-11-15T22:00:00Z"), cal.weekRange(t, LocalDate.of(2026, 11, 20))[0]);
        assertEquals(Instant.parse("2026-11-21T22:00:00Z"), cal.dayRange(t, LocalDate.of(2026, 11, 22))[0]);
        assertEquals(LocalDate.of(2026, 11, 1), cal.businessDateOf(t, Instant.parse("2026-10-31T22:30:00Z")));
        assertEquals("22 Nov 2026 23:59 SAST", cal.format(t, clock.now));
    }

    private static String src(String p) throws Exception { return Files.readString(Path.of("src/main/java/za/co/handyflow/platform/" + p)); }

    @Test
    void theMigrationAddsAZoneDefaultingToSouthAfrica() throws Exception {
        String m = Files.readString(Path.of("src/main/resources/db/migration/V861__tenants_time_zone.sql"));
        assertTrue(m.contains("ADD COLUMN IF NOT EXISTS time_zone VARCHAR(60) NOT NULL DEFAULT 'Africa/Johannesburg'"));
        assertTrue(m.contains("chk_tenants_time_zone"));
    }

    @Test
    void theClockBeanYieldsToAnyOtherAndIsUtc() throws Exception {
        String c = src("shared/ClockConfig.java");
        assertTrue(c.contains("@ConditionalOnMissingBean(Clock.class)"));
        assertTrue(c.contains("Clock.systemUTC()"));
    }

    @Test
    void theReportsDecideMonthsInTheCompanyZone() throws Exception {
        String r = src("security/application/internal/ReportingService.java");
        assertEquals(4, r.split("calendar\\.monthRange\\(tenantId, month\\)", -1).length - 1);
        assertFalse(r.contains("ZoneOffset"));
        assertFalse(r.contains("atStartOfDay"));
        String p = src("security/application/internal/PdfReportService.java");
        assertFalse(p.contains("'UTC'"));
        assertTrue(p.contains("calendar.format(tenantId"));
        String s = src("security/application/internal/SecurityGuardPayStatementPdfService.java");
        assertFalse(s.contains("ZoneOffset"));
        assertTrue(s.contains("DATE_FMT.withZone(zone)"));
        assertTrue(s.contains("calendar.zone(tenantId)"));
    }
}
