package za.co.handyflow.platform.security.application.internal;

import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.shared.HandyFlowException;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class BillingRulesTest {

    private static final ZoneId SAST = ZoneId.of("Africa/Johannesburg");
    private static final YearMonth OCT = YearMonth.of(2026, 10);
    private static final String DONE = "COMPLETED";

    private static Instant at(String local) { return java.time.LocalDateTime.parse(local).atZone(SAST).toInstant(); }
    private static BillingRules.Shift shift(String from, String to) { return new BillingRules.Shift(at(from), at(to), null, null, DONE); }
    private static BillingRules.Shift shift(String from, String to, String status) { return new BillingRules.Shift(at(from), at(to), null, null, status); }
    private static BillingRules.RateInfo hourly(long cents, String from, String to) {
        return new BillingRules.RateInfo(UUID.randomUUID(), "HOURLY", cents, false, LocalDate.parse(from), to == null ? null : LocalDate.parse(to));
    }
    private static BillingRules.RateInfo monthly(long cents, String from, String to) {
        return new BillingRules.RateInfo(UUID.randomUUID(), "MONTHLY_FIXED", cents, false, LocalDate.parse(from), to == null ? null : LocalDate.parse(to));
    }
    private static BillingRules.Preview price(List<BillingRules.Shift> s, BillingRules.RateInfo... r) {
        return BillingRules.price(OCT, SAST, BillingRules.Basis.SCHEDULED, s, List.of(r));
    }

    // ── hourly ──────────────────────────────────────────────────────────────

    @Test
    void completedHoursTimesTheHourlyRate() {
        List<BillingRules.Shift> shifts = new ArrayList<>();
        for (int d = 1; d <= 10; d++) shifts.add(shift("2026-10-" + String.format("%02d", d) + "T06:00", "2026-10-" + String.format("%02d", d) + "T18:00"));
        BillingRules.Preview p = price(shifts, hourly(8500, "2026-01-01", null));
        assertEquals(120 * 60, p.billedMinutes());
        assertEquals(1, p.lines().size());
        assertEquals(120L * 8500, p.totalCents());
        assertEquals(0, p.unpricedMinutes());
    }

    @Test
    void partHoursAreRoundedHalfUpToTheCent() {
        // 8500 c/h: 1 minute = 141.67 -> 142; 7 minutes = 991.67 -> 992; 3 minutes = 425.0 exactly
        assertEquals(142, price(List.of(shift("2026-10-05T10:00", "2026-10-05T10:01")), hourly(8500, "2026-01-01", null)).totalCents());
        assertEquals(992, price(List.of(shift("2026-10-05T10:00", "2026-10-05T10:07")), hourly(8500, "2026-01-01", null)).totalCents());
        assertEquals(425, price(List.of(shift("2026-10-05T10:00", "2026-10-05T10:03")), hourly(8500, "2026-01-01", null)).totalCents());
        // 6 minutes at 5 c/h = 0.5 -> 1 (half rounds up)
        assertEquals(1, price(List.of(shift("2026-10-05T10:00", "2026-10-05T10:06")), hourly(5, "2026-01-01", null)).totalCents());
    }

    @Test
    void roundingHappensOncePerRateNotPerShift() {
        // 60 shifts of 1 minute at 8500 c/h = 60 minutes = exactly 8500, not 60 x 142
        List<BillingRules.Shift> shifts = new ArrayList<>();
        Instant base = at("2026-10-05T10:00");
        for (int i = 0; i < 60; i++) shifts.add(new BillingRules.Shift(base.plusSeconds(i * 60L), base.plusSeconds(i * 60L + 60), null, null, DONE));
        assertEquals(8500, price(shifts, hourly(8500, "2026-01-01", null)).totalCents());
    }

    @Test
    void onlyCompletedShiftsAreBilledTheRestAreCounted() {
        BillingRules.Preview p = price(List.of(shift("2026-10-01T06:00", "2026-10-01T18:00"), shift("2026-10-02T06:00", "2026-10-02T18:00", "MISSED"),
                shift("2026-10-03T06:00", "2026-10-03T18:00", "CANCELLED"), shift("2026-10-04T06:00", "2026-10-04T18:00", "MISSED"),
                shift("2026-10-05T06:00", "2026-10-05T18:00", "SCHEDULED")), hourly(10000, "2026-01-01", null));
        assertEquals(12 * 60, p.billedMinutes());
        assertEquals(12 * 10000L, p.totalCents());
        assertEquals(2, (int) p.notBilled().get("MISSED"));
        assertEquals(1, (int) p.notBilled().get("CANCELLED"));
        assertEquals(1, (int) p.notBilled().get("SCHEDULED"));
        assertFalse(p.notBilled().containsKey("COMPLETED"));
    }

    // ── the month edges ─────────────────────────────────────────────────────

    @Test
    void aNightShiftOverMonthEndIsSplit() {
        // 22:00 on 31 Oct to 06:00 on 1 Nov: two hours in October, six in November
        List<BillingRules.Shift> s = List.of(shift("2026-10-31T22:00", "2026-11-01T06:00"));
        assertEquals(120, price(s, hourly(6000, "2026-01-01", null)).billedMinutes());
        BillingRules.Preview nov = BillingRules.price(YearMonth.of(2026, 11), SAST, BillingRules.Basis.SCHEDULED, s, List.of(hourly(6000, "2026-01-01", null)));
        assertEquals(360, nov.billedMinutes());
        // the same shift is priced once across the two months
        assertEquals(8 * 60, price(s, hourly(6000, "2026-01-01", null)).billedMinutes() + nov.billedMinutes());
    }

    @Test
    void theMonthIsDecidedInTheCompanyZoneNotUtc() {
        // 23:30 UTC on 30 Sep is 01:30 on 1 Oct in Johannesburg: it belongs to October
        BillingRules.Shift s = new BillingRules.Shift(Instant.parse("2026-09-30T23:30:00Z"), Instant.parse("2026-10-01T03:30:00Z"), null, null, DONE);
        assertEquals(240, price(List.of(s), hourly(6000, "2026-01-01", null)).billedMinutes());
        // in Auckland (UTC+13 in October) the same instants are 12:30 to 16:30 on 1 Oct as well
        assertEquals(240, BillingRules.price(OCT, ZoneId.of("Pacific/Auckland"), BillingRules.Basis.SCHEDULED, List.of(s), List.of(hourly(6000, "2026-01-01", null))).billedMinutes());
        // but for New York (UTC-4) it is 19:30 to 23:30 on 30 Sep: not October
        assertEquals(0, BillingRules.price(OCT, ZoneId.of("America/New_York"), BillingRules.Basis.SCHEDULED, List.of(s), List.of(hourly(6000, "2026-01-01", null))).billedMinutes());
    }

    @Test
    void aShiftWhollyOutsideTheMonthAddsNothing() {
        assertEquals(0, price(List.of(shift("2026-09-30T06:00", "2026-09-30T18:00"), shift("2026-11-01T06:00", "2026-11-01T18:00")), hourly(6000, "2026-01-01", null)).billedMinutes());
    }

    // ── rate changes ────────────────────────────────────────────────────────

    @Test
    void eachShiftUsesTheRateInForceOnTheDayItStarts() {
        BillingRules.RateInfo old = hourly(8000, "2026-01-01", "2026-10-15");
        BillingRules.RateInfo now = hourly(9000, "2026-10-16", null);
        BillingRules.Preview p = price(List.of(shift("2026-10-15T06:00", "2026-10-15T14:00"), shift("2026-10-16T06:00", "2026-10-16T14:00")), old, now);
        assertEquals(2, p.lines().size());
        assertEquals(8 * 8000L + 8 * 9000L, p.totalCents());
        assertEquals(8 * 60, p.lines().get(0).minutes());
    }

    @Test
    void theStartDayIsInTheCompanyZone() {
        // starts 23:30 UTC on 15 Oct = 01:30 on 16 Oct Johannesburg, so it takes the new rate
        BillingRules.Shift s = new BillingRules.Shift(Instant.parse("2026-10-15T23:30:00Z"), Instant.parse("2026-10-16T01:30:00Z"), null, null, DONE);
        BillingRules.Preview p = price(List.of(s), hourly(8000, "2026-01-01", "2026-10-15"), hourly(9000, "2026-10-16", null));
        assertEquals(2 * 9000L, p.totalCents());
    }

    @Test
    void ifRatesEverOverlapTheOneThatStartedLastWins() {
        // the database and the rate rules prevent this; if bad data ever gets in, the newer rate is the one the client agreed last
        BillingRules.Preview p = price(List.of(shift("2026-10-20T06:00", "2026-10-20T14:00")), hourly(8000, "2026-01-01", null), hourly(9000, "2026-10-10", null));
        assertEquals(8 * 9000L, p.totalCents());
        BillingRules.Preview q = price(List.of(shift("2026-10-20T06:00", "2026-10-20T14:00")), hourly(9000, "2026-10-10", null), hourly(8000, "2026-01-01", null));
        assertEquals(8 * 9000L, q.totalCents());
    }

    @Test
    void hoursWithNoRateAreReportedNotPricedNotLost() {
        BillingRules.Preview p = price(List.of(shift("2026-10-01T06:00", "2026-10-01T14:00"), shift("2026-10-20T06:00", "2026-10-20T14:00")), hourly(8000, "2026-10-10", null));
        assertEquals(8 * 60, p.unpricedMinutes());
        assertEquals(8 * 8000L, p.totalCents());
        assertEquals(16 * 60, p.billedMinutes());
    }

    @Test
    void noRatesAtAllMeansNothingIsPriced() {
        BillingRules.Preview p = price(List.of(shift("2026-10-01T06:00", "2026-10-01T14:00")));
        assertEquals(0, p.totalCents());
        assertEquals(8 * 60, p.unpricedMinutes());
        assertTrue(p.lines().isEmpty());
    }

    // ── monthly fixed ───────────────────────────────────────────────────────

    @Test
    void aFixedMonthlyFeeIsChargedOnceWhateverTheHours() {
        BillingRules.Preview p = price(List.of(shift("2026-10-01T06:00", "2026-10-01T18:00"), shift("2026-10-02T06:00", "2026-10-02T18:00")), monthly(4_500_000, "2026-01-01", null));
        assertEquals(4_500_000L, p.totalCents());
        assertEquals(24 * 60, p.fixedFeeMinutes());
        assertEquals(1, p.lines().size());
        assertEquals(31, p.lines().get(0).days());
        assertEquals(24 * 60, p.lines().get(0).minutes());
    }

    @Test
    void aFixedFeeWithNoShiftsIsStillCharged() {
        BillingRules.Preview p = price(List.of(), monthly(3_100_000, "2026-01-01", null));
        assertEquals(3_100_000L, p.totalCents());
    }

    @Test
    void aFixedFeeThatStartsMidMonthIsChargedByDays() {
        // in force from 17 Oct: 15 of 31 days
        BillingRules.Preview p = price(List.of(), monthly(3_100_000, "2026-10-17", null));
        assertEquals(15, p.lines().get(0).days());
        assertEquals(1_500_000L, p.totalCents());
    }

    @Test
    void aFixedFeeThatEndsMidMonthIsChargedByDaysInclusive() {
        BillingRules.Preview p = price(List.of(), monthly(3_100_000, "2026-01-01", "2026-10-10"));
        assertEquals(10, p.lines().get(0).days());
        assertEquals(1_000_000L, p.totalCents());
    }

    @Test
    void proRataFixedFeesRoundHalfUp() {
        // 100 cents x 1 day / 31 = 3.2258 -> 3 ; 100 x 16 / 31 = 51.6129 -> 52
        assertEquals(3, price(List.of(), monthly(100, "2026-10-31", null)).totalCents());
        assertEquals(52, price(List.of(), monthly(100, "2026-10-16", null)).totalCents());
        // exactly half: 31 cents x 1 day / 31... use 62 x 1 / 31 = 2.0 ; 1 x 31/62 impossible, check 15.5 -> 16 via 31 cents x 15 / 30 days (November)
        assertEquals(16, BillingRules.price(YearMonth.of(2026, 11), SAST, BillingRules.Basis.SCHEDULED, List.of(), List.of(monthly(31, "2026-11-16", null))).totalCents());
    }

    @Test
    void anHourlyRateThenAFixedFeeBothAppear() {
        BillingRules.Preview p = price(List.of(shift("2026-10-05T06:00", "2026-10-05T14:00"), shift("2026-10-20T06:00", "2026-10-20T14:00")),
                hourly(8000, "2026-01-01", "2026-10-15"), monthly(3_100_000, "2026-10-16", null));
        assertEquals(2, p.lines().size());
        assertEquals(8 * 8000L + 16 * 100_000L, p.totalCents());
        assertEquals(8 * 60, p.fixedFeeMinutes());
    }

    @Test
    void aRateThatDoesNotTouchTheMonthIsNotListed() {
        assertTrue(price(List.of(), monthly(3_100_000, "2026-11-01", null)).lines().isEmpty());
        assertTrue(price(List.of(), hourly(8000, "2025-01-01", "2025-12-31")).lines().isEmpty());
    }

    // ── scheduled or actual ─────────────────────────────────────────────────

    private static BillingRules.Shift clocked(String from, String to, String aFrom, String aTo) {
        return new BillingRules.Shift(at(from), at(to), aFrom == null ? null : at(aFrom), aTo == null ? null : at(aTo), DONE);
    }

    @Test
    void scheduledBasisIgnoresClockedTimes() {
        BillingRules.Preview p = price(List.of(clocked("2026-10-05T06:00", "2026-10-05T18:00", "2026-10-05T06:10", "2026-10-05T17:30")), hourly(6000, "2026-01-01", null));
        assertEquals(12 * 60, p.billedMinutes());
        assertEquals(12 * 60, p.scheduledMinutes());
        assertEquals(11 * 60 + 20, p.actualMinutes());
    }

    @Test
    void actualBasisUsesClockedTimesWhereBothAreRecorded() {
        BillingRules.Preview p = BillingRules.price(OCT, SAST, BillingRules.Basis.ACTUAL,
                List.of(clocked("2026-10-05T06:00", "2026-10-05T18:00", "2026-10-05T06:10", "2026-10-05T17:30")), List.of(hourly(6000, "2026-01-01", null)));
        assertEquals(11 * 60 + 20, p.billedMinutes());
        assertEquals(12 * 60, p.scheduledMinutes());
        assertEquals(0, p.shiftsWithoutActual());
    }

    @Test
    void actualBasisFallsBackToScheduledAndSaysSo() {
        BillingRules.Preview p = BillingRules.price(OCT, SAST, BillingRules.Basis.ACTUAL,
                List.of(clocked("2026-10-05T06:00", "2026-10-05T18:00", "2026-10-05T06:00", null), clocked("2026-10-06T06:00", "2026-10-06T18:00", null, null)),
                List.of(hourly(6000, "2026-01-01", null)));
        assertEquals(24 * 60, p.billedMinutes());
        assertEquals(2, p.shiftsWithoutActual());
    }

    @Test
    void anActualEndBeforeTheStartIsTreatedAsNotRecorded() {
        BillingRules.Preview p = BillingRules.price(OCT, SAST, BillingRules.Basis.ACTUAL,
                List.of(clocked("2026-10-05T06:00", "2026-10-05T18:00", "2026-10-05T10:00", "2026-10-05T09:00")), List.of(hourly(6000, "2026-01-01", null)));
        assertEquals(12 * 60, p.billedMinutes());
        assertEquals(1, p.shiftsWithoutActual());
    }

    @Test
    void theBasisIsParsedStrictly() {
        assertEquals(BillingRules.Basis.SCHEDULED, BillingRules.Basis.parse(null));
        assertEquals(BillingRules.Basis.SCHEDULED, BillingRules.Basis.parse("  "));
        assertEquals(BillingRules.Basis.ACTUAL, BillingRules.Basis.parse(" actual "));
        assertEquals("BILLING_BASIS", assertThrows(HandyFlowException.class, () -> BillingRules.Basis.parse("WORKED")).getErrorCode());
    }

    // ── vat ─────────────────────────────────────────────────────────────────

    @Test
    void mixedVatRatesAreFlagged() {
        BillingRules.RateInfo inc = new BillingRules.RateInfo(UUID.randomUUID(), "HOURLY", 9000, true, LocalDate.parse("2026-10-16"), null);
        BillingRules.Preview p = price(List.of(shift("2026-10-05T06:00", "2026-10-05T14:00"), shift("2026-10-20T06:00", "2026-10-20T14:00")), hourly(8000, "2026-01-01", "2026-10-15"), inc);
        assertTrue(p.mixedVat());
        assertFalse(price(List.of(shift("2026-10-05T06:00", "2026-10-05T14:00")), hourly(8000, "2026-01-01", null)).mixedVat());
        assertFalse(price(List.of(shift("2026-10-05T06:00", "2026-10-05T14:00")), inc).mixedVat());
    }

    // ── helpers ─────────────────────────────────────────────────────────────

    @Test
    void roundedDivIsHalfUp() {
        assertEquals(0, BillingRules.roundedDiv(0, 60));
        assertEquals(1, BillingRules.roundedDiv(30, 60));
        assertEquals(0, BillingRules.roundedDiv(29, 60));
        assertEquals(2, BillingRules.roundedDiv(90, 60));
        assertEquals(8500, BillingRules.roundedDiv(8500L * 60, 60));
    }

    @Test
    void largeAmountsDoNotOverflow() {
        // R100 000 per hour for a whole month of minutes
        long minutes = 31L * 24 * 60;
        assertEquals(10_000_000L * 744, BillingRules.roundedDiv(Math.multiplyExact(10_000_000L, minutes), 60));
    }

    // ── what the code does ──────────────────────────────────────────────────

    private static String src(String p) throws Exception { return Files.readString(Path.of("src/main/java/za/co/handyflow/platform/security/" + p)); }

    @Test
    void theServiceIsTenantScopedAndOnlyReads() throws Exception {
        String s = src("application/internal/BillingPreviewService.java");
        assertEquals(3, count(s, "tenant_id = ?"));
        assertFalse(s.contains("INSERT") || s.contains("UPDATE ") || s.contains("DELETE"), "a preview never writes");
        assertEquals(2, count(s, "@Transactional(readOnly = true)"));
        assertTrue(s.contains("za.co.handyflow.platform.shared.BusinessCalendar.zoneOf(calendar, tenantId)"), "the company's zone, never the server's");
        assertTrue(s.contains("MonthlyPackRules.month(monthAsked, now, zone)"), "a future month is refused the same way as the monthly pack");
    }

    @Test
    void theEndpointNeedsAdminAndTheSiteInScope() throws Exception {
        String c = src("api/BillingPreviewController.java");
        assertTrue(c.contains("@PreAuthorize(\"hasAuthority('SECURITY_ADMIN')\")"));
        assertTrue(c.contains("featureGuard.requireModule(\"security\");\n        site(siteId);"));
        assertTrue(c.contains("required = false"));
        assertFalse(c.contains("tenantId\""));
    }

    private static int count(String s, String what) {
        int n = 0, i = 0;
        while ((i = s.indexOf(what, i)) >= 0) { n++; i += what.length(); }
        return n;
    }
}
