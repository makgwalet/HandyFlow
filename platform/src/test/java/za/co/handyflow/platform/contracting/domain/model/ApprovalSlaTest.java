package za.co.handyflow.platform.contracting.domain.model;

import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.contracting.domain.model.ApprovalSla.Result;
import za.co.handyflow.platform.contracting.domain.model.ApprovalSla.Sample;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ApprovalSlaTest {

    static final ZoneId ZONE = ZoneId.of("Africa/Johannesburg");
    static final ApprovalWorkingTime CALENDAR = ApprovalWorkingTime.calendar(ZONE);
    static final long TARGET = 72 * 60;
    // fixed ids: approvers with the same number of decisions are listed by id, so the order must not depend on chance
    static final UUID REQ = UUID.fromString("00000000-0000-0000-0000-00000000000a");
    static final UUID ANN = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
    static final UUID BEN = UUID.fromString("00000000-0000-0000-0000-0000000000b2");
    static final Instant NOW = at("2026-11-24T12:00");

    static Instant at(String local) { return LocalDateTime.parse(local).atZone(ZONE).toInstant(); }

    static Sample decided(String action, UUID by, long waitedMinutes, int level) {
        Instant asked = at("2026-11-10T08:00");
        return new Sample(UUID.randomUUID(), action, "APPROVED", REQ, asked, asked.plusSeconds(waitedMinutes * 60), by, level, false);
    }

    static Sample waiting(String action, long waitedMinutes, int level) {
        return new Sample(UUID.randomUUID(), action, "PENDING", REQ, NOW.minusSeconds(waitedMinutes * 60), null, null, level, false);
    }

    static Sample closedWithoutDecision(String status) {
        return new Sample(UUID.randomUUID(), "TERMINATE", status, REQ, at("2026-11-01T08:00"), null, null, 0, false);
    }

    @Test
    void nothingToReport() {
        Result r = ApprovalSla.compute(List.of(), CALENDAR, NOW, TARGET);
        assertEquals(0, r.requested());
        assertNull(r.averageMinutes());
        assertNull(r.medianMinutes());
        assertNull(r.ninetiethPercentileMinutes());
        assertNull(r.decidedWithinTargetPercent());
        assertTrue(r.byAction().isEmpty());
        assertTrue(r.overdue().isEmpty());
    }

    @Test
    void averageMedianAndNinetiethPercentile() {
        Result r = ApprovalSla.compute(List.of(
                decided("SEND_FOR_SIGNING", ANN, 60, 0), decided("SEND_FOR_SIGNING", ANN, 120, 0),
                decided("SEND_FOR_SIGNING", ANN, 180, 0), decided("SEND_FOR_SIGNING", ANN, 240, 0)), CALENDAR, NOW, TARGET);
        assertEquals(4, r.decided());
        assertEquals(150L, r.averageMinutes());
        assertEquals(120L, r.medianMinutes());
        assertEquals(240L, r.ninetiethPercentileMinutes());
        assertEquals(100.0, r.decidedWithinTargetPercent());
    }

    @Test
    void shareDecidedWithinTarget() {
        Result r = ApprovalSla.compute(List.of(
                decided("TERMINATE", ANN, 60, 0), decided("TERMINATE", ANN, TARGET, 0),
                decided("TERMINATE", BEN, TARGET + 1, 3), decided("TERMINATE", BEN, 5000, 3)), CALENDAR, NOW, TARGET);
        assertEquals(50.0, r.decidedWithinTargetPercent());
        assertEquals(2, r.escalated());
    }

    @Test
    void waitingApprovalsPastTheTargetAreListedLongestFirst() {
        Sample fresh = waiting("TERMINATE", 10 * 60, 0);
        Sample old = waiting("SEND_FOR_SIGNING", 100 * 60, 3);
        Sample older = waiting("PUBLISH_CLAUSE", 200 * 60, 3);
        Result r = ApprovalSla.compute(List.of(fresh, old, older), CALENDAR, NOW, TARGET);
        assertEquals(3, r.waiting());
        assertEquals(2, r.waitingPastTarget());
        assertEquals(List.of(older.id(), old.id()), r.overdue().stream().map(ApprovalSla.Overdue::id).toList());
        assertEquals(200 * 60, r.overdue().get(0).waitedMinutes());
    }

    @Test
    void withdrawnAndOutOfDateRequestsAreNotDecisions() {
        Result r = ApprovalSla.compute(List.of(closedWithoutDecision("CANCELLED"), closedWithoutDecision("INVALIDATED"),
                decided("TERMINATE", ANN, 60, 0)), CALENDAR, NOW, TARGET);
        assertEquals(3, r.requested());
        assertEquals(1, r.decided());
        assertEquals(2, r.cancelledOrOutOfDate());
        assertEquals(60L, r.averageMinutes());
    }

    @Test
    void rejectedAndUsedUpRequestsCountAsDecided() {
        Instant asked = at("2026-11-10T08:00");
        Sample rejected = new Sample(UUID.randomUUID(), "TERMINATE", "REJECTED", REQ, asked, asked.plusSeconds(600), ANN, 0, false);
        Sample consumed = new Sample(UUID.randomUUID(), "TERMINATE", "CONSUMED", REQ, asked, asked.plusSeconds(1200), BEN, 0, false);
        Result r = ApprovalSla.compute(List.of(rejected, consumed), CALENDAR, NOW, TARGET);
        assertEquals(2, r.decided());
        assertEquals(15L, r.averageMinutes());
    }

    @Test
    void remindedEscalatedAndExceptionsAreCounted() {
        Instant asked = at("2026-11-10T08:00");
        Sample exception = new Sample(UUID.randomUUID(), "TERMINATE", "APPROVED", REQ, asked, asked.plusSeconds(60), REQ, 0, true);
        Result r = ApprovalSla.compute(List.of(waiting("TERMINATE", 30, 0), waiting("TERMINATE", 30 * 60, 1),
                waiting("TERMINATE", 50 * 60, 2), decided("TERMINATE", ANN, 80 * 60, 3), exception), CALENDAR, NOW, TARGET);
        assertEquals(3, r.reminded());
        assertEquals(1, r.escalated());
        assertEquals(1, r.soleUserExceptions());
    }

    @Test
    void groupedByActionAndByApproverBusiestFirst() {
        Result r = ApprovalSla.compute(List.of(
                decided("TERMINATE", ANN, 100, 0), decided("TERMINATE", BEN, 300, 3), decided("TERMINATE", ANN, 200, 0),
                decided("PUBLISH_CLAUSE", BEN, 60, 0)), CALENDAR, NOW, TARGET);
        assertEquals("TERMINATE", r.byAction().get(0).key());
        assertEquals(3, r.byAction().get(0).decided());
        assertEquals(200L, r.byAction().get(0).averageMinutes());
        assertEquals(1, r.byAction().get(0).escalated());
        assertEquals("PUBLISH_CLAUSE", r.byAction().get(1).key());
        assertEquals(ANN.toString(), r.byApprover().get(0).key());
        assertEquals(150L, r.byApprover().get(0).averageMinutes());
        assertEquals(BEN.toString(), r.byApprover().get(1).key());
        assertEquals(1, r.byApprover().get(1).escalated());
    }

    @Test
    void approvalsWithoutAnApproverStillCountInTheTotals() {
        Instant asked = at("2026-11-10T08:00");
        Sample noApprover = new Sample(UUID.randomUUID(), "TERMINATE", "APPROVED", REQ, asked, asked.plusSeconds(600), null, 0, false);
        Result r = ApprovalSla.compute(List.of(noApprover), CALENDAR, NOW, TARGET);
        assertEquals(1, r.decided());
        assertTrue(r.byApprover().isEmpty());
    }

    @Test
    void theBusinessClockIsWhatIsMeasured() {
        ApprovalWorkingTime week = ApprovalWorkingTime.businessHours(ZONE, LocalTime.of(8, 0), LocalTime.of(17, 0), ApprovalWorkingTime.MON_TO_FRI, Set.of());
        // asked Friday 16:00, decided Monday 09:00: three days on the calendar, two hours on the working clock
        Sample s = new Sample(UUID.randomUUID(), "TERMINATE", "APPROVED", REQ, at("2026-11-20T16:00"), at("2026-11-23T09:00"), ANN, 0, false);
        assertEquals(120L, ApprovalSla.compute(List.of(s), week, NOW, TARGET).averageMinutes());
        assertEquals(65 * 60L, ApprovalSla.compute(List.of(s), CALENDAR, NOW, TARGET).averageMinutes());
    }

    @Test
    void percentileIsNearestRank() {
        List<Long> v = List.of(10L, 20L, 30L, 40L, 50L, 60L, 70L, 80L, 90L, 100L);
        assertEquals(50L, ApprovalSla.percentile(v, 50));
        assertEquals(90L, ApprovalSla.percentile(v, 90));
        assertEquals(100L, ApprovalSla.percentile(v, 100));
        assertEquals(10L, ApprovalSla.percentile(v, 1));
        assertNull(ApprovalSla.percentile(List.of(), 50));
    }
}
