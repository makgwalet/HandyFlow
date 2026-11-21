package za.co.handyflow.platform.security.application.internal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

class PayrollRulesTest {

    private static Instant at(String iso) { return Instant.parse(iso); }

    @Test @DisplayName("a period runs from midnight to midnight South African time, not UTC")
    void window() {
        assertEquals(at("2026-10-31T22:00:00Z"), PayrollRules.windowStart(LocalDate.of(2026, 11, 1)));
        assertEquals(at("2026-11-30T22:00:00Z"), PayrollRules.windowEnd(LocalDate.of(2026, 11, 30)));
    }

    @Test @DisplayName("a shift at 01:00 on the 1st (23:00 UTC the day before) belongs to the period that contains the 1st")
    void earlyMorningShiftBelongsToTheNewMonth() {
        Instant shift = at("2026-10-31T23:00:00Z");
        Instant from = PayrollRules.windowStart(LocalDate.of(2026, 11, 1)), to = PayrollRules.windowEnd(LocalDate.of(2026, 11, 30));
        assertTrue(!shift.isBefore(from) && shift.isBefore(to), "in November");
        assertFalse(!shift.isBefore(PayrollRules.windowStart(LocalDate.of(2026, 10, 1))) && shift.isBefore(PayrollRules.windowEnd(LocalDate.of(2026, 10, 31))), "not in October");
        assertEquals(LocalDate.of(2026, 11, 1), PayrollRules.shiftDate(shift));
    }

    @Test @DisplayName("the shift date is the South African date")
    void shiftDate() {
        assertEquals(LocalDate.of(2026, 11, 20), PayrollRules.shiftDate(at("2026-11-19T22:30:00Z")));
        assertEquals(LocalDate.of(2026, 11, 19), PayrollRules.shiftDate(at("2026-11-19T21:59:00Z")));
    }

    @Test @DisplayName("hours up to the standard are regular, the rest is overtime")
    void split() {
        PayrollRules.Split twelve = PayrollRules.split(at("2026-11-19T04:00:00Z"), at("2026-11-19T16:00:00Z"), 9);
        assertEquals(new BigDecimal("9.00"), twelve.regular());
        assertEquals(new BigDecimal("3.00"), twelve.overtime());
        PayrollRules.Split eight = PayrollRules.split(at("2026-11-19T04:00:00Z"), at("2026-11-19T12:00:00Z"), 9);
        assertEquals(new BigDecimal("8.00"), eight.regular());
        assertEquals(new BigDecimal("0.00"), eight.overtime());
        PayrollRules.Split exactly = PayrollRules.split(at("2026-11-19T04:00:00Z"), at("2026-11-19T13:00:00Z"), 9);
        assertEquals(new BigDecimal("9.00"), exactly.regular());
        assertEquals(new BigDecimal("0.00"), exactly.overtime());
    }

    @Test @DisplayName("minutes become hours exactly, rounded half up to two places")
    void minutes() {
        assertEquals(new BigDecimal("7.50"), PayrollRules.split(at("2026-11-19T04:00:00Z"), at("2026-11-19T11:30:00Z"), 9).regular());
        assertEquals(new BigDecimal("0.02"), PayrollRules.split(at("2026-11-19T04:00:00Z"), at("2026-11-19T04:01:00Z"), 9).regular(), "1 minute = 0.0166 -> 0.02");
        assertEquals(new BigDecimal("8.33"), PayrollRules.split(at("2026-11-19T04:00:00Z"), at("2026-11-19T12:20:00Z"), 9).regular(), "8h20 = 8.333");
    }

    @Test @DisplayName("an end before the start pays nothing instead of a negative amount")
    void reversed() {
        PayrollRules.Split s = PayrollRules.split(at("2026-11-19T12:00:00Z"), at("2026-11-19T04:00:00Z"), 9);
        assertEquals(new BigDecimal("0.00"), s.regular());
        assertEquals(new BigDecimal("0.00"), s.overtime());
    }

    @Test @DisplayName("a different standard day changes where overtime starts")
    void standardHours() {
        PayrollRules.Split s = PayrollRules.split(at("2026-11-19T04:00:00Z"), at("2026-11-19T16:00:00Z"), 8);
        assertEquals(new BigDecimal("8.00"), s.regular());
        assertEquals(new BigDecimal("4.00"), s.overtime());
    }
}
