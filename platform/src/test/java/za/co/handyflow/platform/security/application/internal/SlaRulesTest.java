package za.co.handyflow.platform.security.application.internal;

import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.shared.HandyFlowException;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static za.co.handyflow.platform.security.application.internal.SlaRules.*;

class SlaRulesTest {

    private static final Instant T0 = Instant.parse("2026-11-23T06:00:00Z");
    private static final Target TGT = new Target(10, 120);
    private static Instant at(long min) { return T0.plusSeconds(min * 60); }
    private static String code(Runnable r) { return assertThrows(HandyFlowException.class, r::run).getErrorCode(); }

    // ---- targets ----

    @Test void severityIsCheckedAndTidied() {
        assertEquals("HIGH", severity(" high "));
        for (String bad : new String[]{null, "", "URGENT", "hi gh"}) assertEquals("SLA_SEVERITY", code(() -> severity(bad)), String.valueOf(bad));
    }

    @Test void minutesMustBeWholeNumbersInRangeAndResolveNotShorterThanAcknowledge() {
        assertEquals(new Target(1, 1), validate(1, 1));
        assertEquals(new Target(10, MAX_MINUTES), validate(10, MAX_MINUTES));
        for (Integer[] bad : new Integer[][]{{null, 5}, {5, null}, {0, 5}, {5, 0}, {-1, 5}, {5, MAX_MINUTES + 1}, {MAX_MINUTES + 1, MAX_MINUTES + 1}}) {
            assertEquals("SLA_MINUTES", code(() -> validate(bad[0], bad[1])), bad[0] + "/" + bad[1]);
        }
        assertEquals("SLA_ORDER", code(() -> validate(60, 30)));
        assertEquals(new Target(30, 30), validate(30, 30), "equal is fine");
    }

    @Test void aSiteTargetBeatsTheCompanyDefault() {
        Target s = new Target(5, 30), c = new Target(10, 120);
        assertSame(s, pick(s, c));
        assertSame(c, pick(null, c));
        assertNull(pick(null, null));
    }

    // ---- one stage ----

    @Test void doneInTimeIsMetAndExactlyOnTheDeadlineStillCounts() {
        Stage a = stage(T0, at(7), 10, at(500));
        assertEquals(State.MET, a.state()); assertEquals(7L, a.takenMinutes()); assertEquals(at(10), a.deadline());
        assertEquals(State.MET, stage(T0, at(10), 10, at(500)).state());
    }

    @Test void doneLateIsBreachedAndSaysByHowMuch() {
        Stage a = stage(T0, at(25), 10, at(500));
        assertEquals(State.BREACHED, a.state()); assertEquals(15L, a.overdueMinutes()); assertEquals(25L, a.takenMinutes());
    }

    @Test void notDoneYetIsPendingUntilTheLastFifthThenAtRiskThenBreached() {
        assertEquals(State.PENDING, stage(T0, null, 100, at(10)).state());
        assertEquals(State.PENDING, stage(T0, null, 100, at(79)).state());
        Stage risk = stage(T0, null, 100, at(80));
        assertEquals(State.AT_RISK, risk.state()); assertEquals(20L, risk.remainingMinutes());
        assertEquals(State.AT_RISK, stage(T0, null, 100, at(100)).state(), "on the deadline it is not yet breached");
        Stage late = stage(T0, null, 100, at(130));
        assertEquals(State.BREACHED, late.state()); assertEquals(30L, late.overdueMinutes());
    }

    @Test void aShortWindowIsAtRiskForAtLeastAMinute() {
        assertEquals(State.AT_RISK, stage(T0, null, 3, at(2)).state());
        assertEquals(State.PENDING, stage(T0, null, 10, at(7)).state());
    }

    @Test void aCompletionBeforeTheStartCountsAsZeroMinutes() {
        assertEquals(0L, stage(T0, at(-5), 10, at(500)).takenMinutes());
    }

    // ---- one incident ----

    @Test void noTargetMeansNotTracked() {
        assertEquals(State.UNTRACKED, evaluate(T0, null, null, null, at(999)).overall());
        assertSame(Evaluation.UNTRACKED, evaluate(T0, null, null, null, at(999)));
        assertSame(Evaluation.UNTRACKED, evaluate(null, null, null, TGT, at(1)));
    }

    @Test void bothStagesInTimeIsMet() {
        Evaluation e = evaluate(T0, at(5), at(90), TGT, at(500));
        assertEquals(State.MET, e.overall()); assertEquals(State.MET, e.acknowledge().state()); assertEquals(State.MET, e.resolve().state());
    }

    @Test void theOverallStateIsTheWorstOfTheTwo() {
        assertEquals(State.BREACHED, evaluate(T0, at(30), at(90), TGT, at(500)).overall(), "acknowledged late");
        assertEquals(State.BREACHED, evaluate(T0, at(5), at(200), TGT, at(500)).overall(), "resolved late");
        assertEquals(State.PENDING, evaluate(T0, at(5), null, TGT, at(30)).overall(), "acknowledged, resolution pending");
        assertEquals(State.AT_RISK, evaluate(T0, at(5), null, TGT, at(100)).overall(), "resolution due soon");
        assertEquals(State.BREACHED, evaluate(T0, null, null, TGT, at(11)).overall(), "never acknowledged");
    }

    @Test void resolvedWithoutAnAcknowledgementCountsAsAcknowledgedWhenResolved() {
        Evaluation quick = evaluate(T0, null, at(8), TGT, at(500));
        assertEquals(State.MET, quick.acknowledge().state());
        assertEquals(8L, quick.acknowledge().takenMinutes());
        Evaluation slow = evaluate(T0, null, at(60), TGT, at(500));
        assertEquals(State.BREACHED, slow.acknowledge().state());
        assertEquals(State.MET, slow.resolve().state());
    }

    @Test void anAcknowledgementAfterTheResolutionUsesTheEarlierTime() {
        Evaluation e = evaluate(T0, at(50), at(8), TGT, at(500));
        assertEquals(8L, e.acknowledge().takenMinutes());
    }

    // ---- many ----

    private static UUID id(int n) { return new UUID(0, n); }
    private static Item item(int n, int site, String name, String sev, Evaluation e) { return new Item(id(n), id(site), name, sev, e); }
    private static final Evaluation MET = evaluate(T0, at(5), at(90), TGT, at(500));
    private static final Evaluation LATE = evaluate(T0, at(30), at(90), TGT, at(500));
    private static final Evaluation OPEN = evaluate(T0, null, null, TGT, at(5));
    private static final Evaluation NONE = Evaluation.UNTRACKED;

    @Test void compliancePercentIsMetOutOfMetPlusBreachedAndIgnoresOpenAndUntracked() {
        Row r = total(List.of(item(1, 1, "A", "HIGH", MET), item(2, 1, "A", "HIGH", MET), item(3, 1, "A", "HIGH", MET), item(4, 1, "A", "HIGH", LATE),
                item(5, 1, "A", "HIGH", OPEN), item(6, 1, "A", "HIGH", NONE)));
        assertEquals(6, r.total()); assertEquals(5, r.tracked()); assertEquals(3, r.met()); assertEquals(1, r.breached()); assertEquals(1, r.pending());
        assertEquals(75.0, r.compliancePct());
    }

    @Test void thirdsRoundToOneDecimal() {
        Row r = total(List.of(item(1, 1, "A", "LOW", MET), item(2, 1, "A", "LOW", LATE), item(3, 1, "A", "LOW", LATE)));
        assertEquals(33.3, r.compliancePct());
    }

    @Test void noMetOrBreachedMeansNoPercentage() {
        assertNull(total(List.of(item(1, 1, "A", "LOW", OPEN))).compliancePct());
        assertNull(total(List.of()).compliancePct());
        assertNull(total(List.of(item(1, 1, "A", "LOW", NONE))).averageAcknowledgeMinutes());
    }

    @Test void averagesUseOnlyIncidentsThatHaveTheTime() {
        Row r = total(List.of(item(1, 1, "A", "HIGH", evaluate(T0, at(4), at(60), TGT, at(500))), item(2, 1, "A", "HIGH", evaluate(T0, at(8), null, TGT, at(30)))));
        assertEquals(6.0, r.averageAcknowledgeMinutes());
        assertEquals(60.0, r.averageResolveMinutes());
    }

    @Test void rowsAreGroupedBySiteAndSeverityInSiteNameThenSeverityOrder() {
        List<Row> rows = summarise(List.of(item(1, 2, "Beta", "LOW", MET), item(2, 1, "alpha", "CRITICAL", MET), item(3, 1, "alpha", "LOW", LATE), item(4, 2, "Beta", "LOW", MET)));
        assertEquals(3, rows.size());
        assertEquals("alpha", rows.get(0).siteName()); assertEquals("LOW", rows.get(0).severity());
        assertEquals("alpha", rows.get(1).siteName()); assertEquals("CRITICAL", rows.get(1).severity());
        assertEquals("Beta", rows.get(2).siteName()); assertEquals(2, rows.get(2).total());
    }

    @Test void theAttentionListHasBreachedFirstMostOverdueFirstThenAtRiskSoonestFirst() {
        Evaluation overdue5 = evaluate(T0, null, null, TGT, at(15)), overdue50 = evaluate(T0, null, null, TGT, at(60));
        Evaluation risk = evaluate(T0, at(1), null, TGT, at(100)), riskSooner = evaluate(T0, at(1), null, TGT, at(115)), pending = evaluate(T0, at(1), null, TGT, at(10));
        List<Item> list = needingAttention(List.of(item(1, 1, "A", "HIGH", risk), item(2, 1, "A", "HIGH", overdue5), item(3, 1, "A", "HIGH", pending),
                item(4, 1, "A", "HIGH", riskSooner), item(5, 1, "A", "HIGH", overdue50), item(6, 1, "A", "HIGH", MET), item(7, 1, "A", "HIGH", NONE)));
        assertEquals(List.of(id(5), id(2), id(4), id(1)), list.stream().map(Item::incidentId).toList());
    }
}
