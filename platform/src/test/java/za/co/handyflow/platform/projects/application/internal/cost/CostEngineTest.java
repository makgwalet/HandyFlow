package za.co.handyflow.platform.projects.application.internal.cost;

import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.projects.application.internal.cost.CostEngine.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class CostEngineTest {

    static BigDecimal n(String s) { return new BigDecimal(s); }

    static Line line(String category, String budgeted, String original, String committed, String actual, String etc) {
        return new Line(UUID.randomUUID(), category, "Some line", n(budgeted),
                original == null ? null : n(original), n(committed), n(actual),
                etc == null ? null : n(etc));
    }

    private static void eq(String expected, BigDecimal actual) {
        assertTrue(n(expected).compareTo(actual) == 0, "expected " + expected + " but was " + actual);
    }

    @Test
    void ordinaryLine_defaultsEtcToRemainingBudget_soEacEqualsBudget() {
        Summary s = CostEngine.summarize(List.of(line("LABOUR", "1000", "1000", "200", "300", null)), 0, null, null);
        LineResult r = s.lines().get(0);
        eq("500", r.remaining());
        eq("500", r.etc());
        assertFalse(r.etcManual());
        eq("1000", r.eac());
        eq("0", r.vac());
        eq("50.00", r.consumedPct());
        assertFalse(r.overBudget());
    }

    @Test
    void overspentLine_forecastsOverrun_andIsFlagged() {
        Summary s = CostEngine.summarize(List.of(line("MATERIALS", "500", "500", "0", "600", null)), 0, null, null);
        LineResult r = s.lines().get(0);
        eq("0", r.etc());
        eq("600", r.eac());
        eq("-100", r.vac());
        eq("-100", r.remaining());
        assertTrue(r.overBudget());
        assertEquals(1, s.overBudgetLineCount());
    }

    @Test
    void manualEtc_overridesTheDefault() {
        Summary s = CostEngine.summarize(List.of(line("SUBCONTRACT", "1000", "1000", "100", "400", "700")), 0, null, null);
        LineResult r = s.lines().get(0);
        assertTrue(r.etcManual());
        eq("1200", r.eac());      // 400 + 100 + 700
        eq("-200", r.vac());
    }

    @Test
    void originalRevisedAdditionsAndAdjustmentsReconcile() {
        List<Line> lines = List.of(
                line("LABOUR", "1200", "1000", "0", "0", null),     // +200 adjustment
                line("MATERIALS", "800", "800", "0", "0", null),    // unchanged
                line("EQUIPMENT", "300", null, "0", "0", null));    // added after baseline
        Summary s = CostEngine.summarize(lines, 0, null, null);

        assertTrue(s.baselineCaptured());
        eq("1800", s.originalBudget());
        eq("2300", s.revisedBudget());
        eq("300", s.additions());
        eq("200", s.netAdjustments());
        // The identity the whole report depends on:
        assertTrue(s.originalBudget().add(s.additions()).add(s.netAdjustments())
                .compareTo(s.revisedBudget()) == 0);
    }

    @Test
    void beforeBaseline_originalComparisonsAreNull() {
        Summary s = CostEngine.summarize(List.of(line("LABOUR", "1000", null, "0", "0", null)), 0, null, null);
        assertFalse(s.baselineCaptured());
        assertNull(s.originalBudget());
        assertNull(s.additions());
        assertNull(s.netAdjustments());
        eq("1000", s.revisedBudget());
    }

    @Test
    void pendingChangeOrders_areExposure_neverBudget() {
        Summary s = CostEngine.summarize(List.of(line("LABOUR", "1000", "1000", "0", "0", null)),
                2, n("150"), null);
        eq("1000", s.revisedBudget());                 // approved budget untouched
        eq("150", s.pendingChangeOrderValue());
        assertEquals(2, s.pendingChangeOrderCount());
        eq("1150", s.eacIncludingPending());           // eac 1000 + 150 exposure
        eq("1000", s.eac());
    }

    @Test
    void contingency_isTrackedSeparately_fromChangeOrderVariations() {
        Line contingency = line("CONTINGENCY", "500", "500", "50", "100", null);
        Line variation = new Line(UUID.randomUUID(), "CONTINGENCY", "Change Order: CO-001 — extra piling",
                n("300"), null, n("0"), n("0"), null);
        Summary s = CostEngine.summarize(List.of(contingency, variation), 0, null, null);

        eq("500", s.contingencyBudget());              // the CO line is NOT management contingency
        eq("150", s.contingencyUsed());
        eq("350", s.contingencyRemaining());
        assertTrue(s.categories().containsKey(CostEngine.VARIATIONS));
        eq("300", s.categories().get(CostEngine.VARIATIONS).budgeted());
    }

    @Test
    void marginForecast_usesContractValueAndEac() {
        Summary s = CostEngine.summarize(List.of(line("LABOUR", "8000", "8000", "0", "0", null)), 0, null, n("10000"));
        eq("2000", s.forecastMargin());
        eq("20.00", s.forecastMarginPct());
    }

    @Test
    void noContractValue_meansNoMarginForecast() {
        Summary s = CostEngine.summarize(List.of(line("LABOUR", "8000", "8000", "0", "0", null)), 0, null, null);
        assertNull(s.forecastMargin());
        assertNull(s.forecastMarginPct());
    }

    @Test
    void categoryRollup_addsUpToTheTotals() {
        Summary s = CostEngine.summarize(List.of(
                line("LABOUR", "1000", "1000", "100", "200", null),
                line("LABOUR", "500", "500", "0", "50", null),
                line("MATERIALS", "700", "700", "0", "0", null)), 0, null, null);
        eq("1500", s.categories().get("LABOUR").budgeted());
        eq("350", s.categories().get("LABOUR").actual().add(s.categories().get("LABOUR").committed()));   // actual 250 + committed 100
        eq("2200", s.categories().values().stream().map(CategoryTotals::budgeted)
                .reduce(BigDecimal.ZERO, BigDecimal::add));
    }

    @Test
    void emptyBudget_hasZeroPercentages_notADivisionByZero() {
        Summary s = CostEngine.summarize(List.of(), 0, null, null);
        eq("0", s.revisedBudget());
        eq("0.00", s.consumedPct());
        assertTrue(s.lines().isEmpty());
    }

    @Test
    void maxTransferable_neverGoesNegative_andRespectsSpentAndCommitted() {
        eq("500", CostEngine.maxTransferable(n("1000"), n("300"), n("200")));
        eq("0",   CostEngine.maxTransferable(n("500"), n("600"), n("0")));
        eq("0",   CostEngine.maxTransferable(n("500"), n("300"), n("200")));
    }
}