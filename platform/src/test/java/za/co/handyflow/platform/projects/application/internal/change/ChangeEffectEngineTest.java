package za.co.handyflow.platform.projects.application.internal.change;

import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.projects.application.internal.change.ChangeEffectEngine.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ChangeEffectEngineTest {

    static BigDecimal n(String s) { return new BigDecimal(s); }

    UUID a = UUID.randomUUID(), b = UUID.randomUUID();

    Map<UUID, LineState> lines() {
        return Map.of(
                a, new LineState(a, n("1000"), n("300"), n("200")),   // 500 free
                b, new LineState(b, n("400"), n("0"), n("0")));
    }

    private static boolean has(List<Problem> ps, String code) {
        return ps.stream().anyMatch(p -> p.code().equals(code));
    }

    @Test
    void validPositiveAllocation_toExistingLine_hasNoProblems() {
        assertTrue(ChangeEffectEngine.validateAllocations(n("250"),
                List.of(new Allocation(a, null, n("250"))), lines()).isEmpty());
    }

    @Test
    void allocationsMustAddUpToTheDeclaredImpact() {
        List<Problem> ps = ChangeEffectEngine.validateAllocations(n("500"),
                List.of(new Allocation(a, null, n("250"))), lines());
        assertTrue(has(ps, "ALLOCATION_MISMATCH"));
    }

    @Test
    void mixedPositiveAndNegativeAllocations_netToTheImpact() {
        // +300 on a new line, −100 off line b → net +200
        List<Problem> ps = ChangeEffectEngine.validateAllocations(n("200"),
                List.of(new Allocation(null, "MATERIALS", n("300")), new Allocation(b, null, n("-100"))), lines());
        assertTrue(ps.isEmpty());
    }

    @Test
    void reduction_cannotTakeALineBelowSpentAndCommitted() {
        // line a: budget 1000, spent+committed 500 → reducing by 600 leaves 400 < 500
        List<Problem> ps = ChangeEffectEngine.validateAllocations(n("-600"),
                List.of(new Allocation(a, null, n("-600"))), lines());
        assertTrue(has(ps, "BELOW_COMMITTED"));
    }

    @Test
    void reduction_downToExactlySpentAndCommitted_isAllowed() {
        assertTrue(ChangeEffectEngine.validateAllocations(n("-500"),
                List.of(new Allocation(a, null, n("-500"))), lines()).isEmpty());
    }

    @Test
    void severalAllocationsOnOneLine_areCheckedTogether() {
        // each −300 alone is fine (700 ≥ 500) but together −600 breaches the floor
        List<Problem> ps = ChangeEffectEngine.validateAllocations(n("-600"),
                List.of(new Allocation(a, null, n("-300")), new Allocation(a, null, n("-300"))), lines());
        assertTrue(has(ps, "BELOW_COMMITTED"));
    }

    @Test
    void negativeImpactWithNoAllocations_isRejected() {
        assertTrue(has(ChangeEffectEngine.validateAllocations(n("-100"), List.of(), lines()), "ALLOCATION_REQUIRED"));
    }

    @Test
    void positiveImpactWithNoAllocations_isTheLegacyForm_andAllowed() {
        assertTrue(ChangeEffectEngine.validateAllocations(n("100"), List.of(), lines()).isEmpty());
    }

    @Test
    void newLine_needsCategory_andCannotBeNegative() {
        assertTrue(has(ChangeEffectEngine.validateAllocations(n("100"),
                List.of(new Allocation(null, " ", n("100"))), lines()), "CATEGORY_REQUIRED"));
        assertTrue(has(ChangeEffectEngine.validateAllocations(n("-100"),
                List.of(new Allocation(null, "LABOUR", n("-100"))), lines()), "NEW_LINE_NEGATIVE"));
    }

    @Test
    void unknownLine_andZeroAmount_areProblems() {
        assertTrue(has(ChangeEffectEngine.validateAllocations(n("50"),
                List.of(new Allocation(UUID.randomUUID(), null, n("50"))), lines()), "LINE_NOT_FOUND"));
        assertTrue(has(ChangeEffectEngine.validateAllocations(n("0"),
                List.of(new Allocation(a, null, n("0"))), lines()), "ZERO_AMOUNT"));
    }

    @Test
    void revisedBudgetAfter_addsAllocations_orLegacyPositiveImpact() {
        assertEquals(0, n("1450").compareTo(ChangeEffectEngine.revisedBudgetAfter(n("1400"), n("50"),
                List.of(new Allocation(a, null, n("50"))))));
        assertEquals(0, n("1500").compareTo(ChangeEffectEngine.revisedBudgetAfter(n("1400"), n("100"), List.of())));
        assertEquals(0, n("1400").compareTo(ChangeEffectEngine.revisedBudgetAfter(n("1400"), n("-100"), List.of())));
    }

    @Test
    void shiftEnd_movesEndAndBaselineByTheSameDays() {
        ScheduleShift s = ChangeEffectEngine.shiftEnd(
                LocalDate.parse("2026-12-15"), LocalDate.parse("2026-12-10"), LocalDate.parse("2026-11-01"), 14);
        assertEquals(LocalDate.parse("2026-12-29"), s.newEnd());
        assertEquals(LocalDate.parse("2026-12-24"), s.newBaselineEnd());
    }

    @Test
    void shiftEnd_allowsRecovery_andNullDates() {
        ScheduleShift s = ChangeEffectEngine.shiftEnd(LocalDate.parse("2026-12-15"), null, null, -5);
        assertEquals(LocalDate.parse("2026-12-10"), s.newEnd());
        assertNull(s.newBaselineEnd());
        ScheduleShift none = ChangeEffectEngine.shiftEnd(null, null, null, 10);
        assertNull(none.newEnd());
    }

    @Test
    void shiftEnd_refusesToPutTheEndBeforeTheStart() {
        try {
            ChangeEffectEngine.shiftEnd(LocalDate.parse("2026-11-10"), null, LocalDate.parse("2026-11-05"), -30);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) { /* ok */ }
    }
}