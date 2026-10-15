package za.co.handyflow.platform.earthmoving;

import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.earthmoving.domain.model.*;
import za.co.handyflow.platform.earthmoving.domain.model.WorkOrderRules.PlanState;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class WorkOrderRulesTest {
    static final LocalDate TODAY = LocalDate.of(2026, 10, 15);

    private static EarthWorkOrder wo(String type) {
        return EarthWorkOrder.create(TenantId.generate(), UUID.randomUUID(), "WO-0001", "Fix hose", null, type, "HIGH",
                TODAY, "MANUAL", null, null, true, UUID.randomUUID());
    }

    // ── creating ─────────────────────────────────────────────────────────────
    @Test void createNeedsTitleAndKnownTypeAndPriority() {
        assertThrows(IllegalArgumentException.class, () -> WorkOrderRules.checkCreate(" ", "REPAIR", "HIGH", null, TODAY));
        assertThrows(IllegalArgumentException.class, () -> WorkOrderRules.checkCreate("x", "NOPE", "HIGH", null, TODAY));
        assertThrows(IllegalArgumentException.class, () -> WorkOrderRules.checkCreate("x", "REPAIR", "SOON", null, TODAY));
        assertDoesNotThrow(() -> WorkOrderRules.checkCreate("x", "REPAIR", null, TODAY, TODAY));
    }
    @Test void aDueDateMoreThanAYearAgoIsRefused() {
        assertThrows(IllegalArgumentException.class, () -> WorkOrderRules.checkCreate("x", "REPAIR", "LOW", TODAY.minusYears(2), TODAY));
    }
    @Test void newWorkOrderIsOpenAndBlocksRelease() {
        EarthWorkOrder w = wo("REPAIR");
        assertEquals(WorkOrderStatus.OPEN, w.getStatus());
        assertTrue(w.isOpenBlocking());
    }

    // ── lifecycle ────────────────────────────────────────────────────────────
    @Test void aJobMustBeStartedBeforeItCanBeCompleted() {
        EarthWorkOrder w = wo("SERVICE");
        assertThrows(IllegalStateException.class, () -> w.complete(UUID.randomUUID(), "all work done", null, BigDecimal.TEN, null, null, null));
    }
    @Test void awaitingPartsCanResumeAndCompleteIsFinal() {
        EarthWorkOrder w = wo("SERVICE");
        w.start(); w.awaitParts(); w.resume();
        w.complete(UUID.randomUUID(), " changed oil ", null, new BigDecimal("1000"), null, null, UUID.randomUUID());
        assertEquals(WorkOrderStatus.COMPLETED, w.getStatus());
        assertEquals("changed oil", w.getWorkDone());
        assertFalse(w.isOpenBlocking());
        assertThrows(IllegalStateException.class, w::start);
        assertThrows(IllegalStateException.class, () -> w.cancel("too late"));
    }
    @Test void cancelNeedsAReason() {
        EarthWorkOrder w = wo("REPAIR");
        assertThrows(IllegalArgumentException.class, () -> w.cancel("  "));
        w.cancel("Raised in error");
        assertEquals(WorkOrderStatus.CANCELLED, w.getStatus());
    }
    @Test void aClosedJobCannotBeReassignedOrChangeBlocking() {
        EarthWorkOrder w = wo("REPAIR");
        w.cancel("duplicate job");
        assertThrows(IllegalStateException.class, () -> w.assign(UUID.randomUUID(), "A"));
        assertThrows(IllegalStateException.class, () -> w.setBlocksRelease(false));
    }
    @Test void turningBlockingOffStopsItBlocking() {
        EarthWorkOrder w = wo("REPAIR");
        w.setBlocksRelease(false);
        assertFalse(w.isOpenBlocking());
    }
    @Test void overdueOnlyWhileOpenAndPastDue() {
        EarthWorkOrder w = wo("REPAIR");
        assertFalse(w.isOverdue(TODAY));
        assertTrue(w.isOverdue(TODAY.plusDays(1)));
        w.cancel("not needed any more");
        assertFalse(w.isOverdue(TODAY.plusDays(1)));
    }

    // ── completion rules ─────────────────────────────────────────────────────
    @Test void aRepairNeedsATestResult() {
        assertThrows(IllegalArgumentException.class, () -> WorkOrderRules.checkComplete("REPAIR", "welded joint", " ", null, null, null));
        assertDoesNotThrow(() -> WorkOrderRules.checkComplete("REPAIR", "welded joint", "Pressure held 10 min", null, null, null));
    }
    @Test void aServiceNeedsTheMeterReadingButNotATestResult() {
        assertThrows(IllegalArgumentException.class, () -> WorkOrderRules.checkComplete("SERVICE", "oil changed", null, null, null, null));
        assertDoesNotThrow(() -> WorkOrderRules.checkComplete("SERVICE", "oil changed", null, BigDecimal.ONE, null, null));
    }
    @Test void negativeHoursAreRefused() {
        assertThrows(IllegalArgumentException.class, () -> WorkOrderRules.checkComplete("TYRE", "tyre swap", null, new BigDecimal("-1"), null, null));
        assertThrows(IllegalArgumentException.class, () -> WorkOrderRules.checkComplete("TYRE", "tyre swap", null, null, new BigDecimal("-1"), null));
        assertThrows(IllegalArgumentException.class, () -> WorkOrderRules.checkComplete("TYRE", "tyre swap", null, null, null, new BigDecimal("-1")));
    }
    @Test void failedCompleteLeavesTheJobInProgress() {
        EarthWorkOrder w = wo("REPAIR");
        w.start();
        assertThrows(IllegalArgumentException.class, () -> w.complete(UUID.randomUUID(), "welded joint", null, null, null, null, null));
        assertEquals(WorkOrderStatus.IN_PROGRESS, w.getStatus());
    }

    // ── cost lines ───────────────────────────────────────────────────────────
    @Test void lineRules() {
        assertThrows(IllegalArgumentException.class, () -> WorkOrderRules.checkLine("PART", "hose", BigDecimal.ZERO, BigDecimal.ONE));
        assertThrows(IllegalArgumentException.class, () -> WorkOrderRules.checkLine("PART", "hose", BigDecimal.ONE, new BigDecimal("-1")));
        assertThrows(IllegalArgumentException.class, () -> WorkOrderRules.checkLine("FUEL", "hose", BigDecimal.ONE, BigDecimal.ONE));
        assertThrows(IllegalArgumentException.class, () -> WorkOrderRules.checkLine("PART", " ", BigDecimal.ONE, BigDecimal.ONE));
        assertDoesNotThrow(() -> WorkOrderRules.checkLine("LABOUR", "2 hrs", new BigDecimal("2"), new BigDecimal("350")));
        assertEquals(new BigDecimal("12.35"), WorkOrderRules.lineTotal(new BigDecimal("2.5"), new BigDecimal("4.94")));
    }

    // ── plans ────────────────────────────────────────────────────────────────
    @Test void planValidation() {
        assertThrows(IllegalArgumentException.class, () -> WorkOrderRules.checkPlan("", "SERVICE", "HOURS", 250, 25));
        assertThrows(IllegalArgumentException.class, () -> WorkOrderRules.checkPlan("a", "SERVICE", "MILES", 250, 25));
        assertThrows(IllegalArgumentException.class, () -> WorkOrderRules.checkPlan("a", "SERVICE", "HOURS", 0, 0));
        assertThrows(IllegalArgumentException.class, () -> WorkOrderRules.checkPlan("a", "SERVICE", "HOURS", 250, 250));
        assertThrows(IllegalArgumentException.class, () -> WorkOrderRules.checkPlan("a", "SERVICE", "HOURS", 250, -1));
        assertDoesNotThrow(() -> WorkOrderRules.checkPlan("a", "SERVICE", "HOURS", 250, 25));
    }
    private static PlanState hours(int interval, int lead, String base, String now) {
        return WorkOrderRules.evaluate("HOURS", interval, lead, new BigDecimal(base), TODAY, new BigDecimal(now), TODAY).state();
    }
    @Test void hourPlanStates() {
        assertEquals(PlanState.OK, hours(250, 25, "1000", "1100"));
        assertEquals(PlanState.DUE_SOON, hours(250, 25, "1000", "1230"));
        assertEquals(PlanState.DUE, hours(250, 25, "1000", "1250"));
        assertEquals(PlanState.DUE, hours(250, 25, "1000", "1275"));
        assertEquals(PlanState.OVERDUE, hours(250, 25, "1000", "1276"));
    }
    @Test void aMeterThatWentBackwardsNeverCountsAsNegativeUse() {
        var s = WorkOrderRules.evaluate("HOURS", 250, 25, new BigDecimal("1000"), TODAY, new BigDecimal("900"), TODAY);
        assertEquals(PlanState.OK, s.state());
        assertEquals(0, BigDecimal.ZERO.compareTo(s.used()));
    }
    @Test void dayPlanStates() {
        assertEquals(PlanState.OK, WorkOrderRules.evaluate("DAYS", 90, 14, null, TODAY.minusDays(10), null, TODAY).state());
        assertEquals(PlanState.DUE_SOON, WorkOrderRules.evaluate("DAYS", 90, 14, null, TODAY.minusDays(80), null, TODAY).state());
        assertEquals(PlanState.DUE, WorkOrderRules.evaluate("DAYS", 90, 14, null, TODAY.minusDays(90), null, TODAY).state());
        assertEquals(PlanState.OVERDUE, WorkOrderRules.evaluate("DAYS", 90, 14, null, TODAY.minusDays(100), null, TODAY).state());
    }
    @Test void aNewPlanCountsFromItsBaselineNotFromZero() {
        EarthMaintenancePlan p = EarthMaintenancePlan.create(TenantId.generate(), UUID.randomUUID(), "250h service", "SERVICE",
                "HOURS", 250, 25, new BigDecimal("4000"), TODAY, "NORMAL", true);
        assertEquals(PlanState.OK, p.status(new BigDecimal("4050"), TODAY).state());
        assertEquals(PlanState.DUE, p.status(new BigDecimal("4250"), TODAY).state());
    }
    @Test void markingDoneRestartsTheCount() {
        EarthMaintenancePlan p = EarthMaintenancePlan.create(TenantId.generate(), UUID.randomUUID(), "250h service", "SERVICE",
                "HOURS", 250, 25, new BigDecimal("4000"), TODAY, "NORMAL", true);
        p.markDone(new BigDecimal("4260"), TODAY);
        assertEquals(PlanState.OK, p.status(new BigDecimal("4300"), TODAY).state());
    }
    @Test void planUpdateIsValidatedAndCanDeactivate() {
        EarthMaintenancePlan p = EarthMaintenancePlan.create(TenantId.generate(), UUID.randomUUID(), "p", "SERVICE",
                "DAYS", 90, 14, null, TODAY, "NORMAL", true);
        assertThrows(IllegalArgumentException.class, () -> p.update("p", 10, 10, "NORMAL", true, true));
        p.update("p2", 120, 10, "high", false, false);
        assertFalse(p.isActive());
        assertEquals("HIGH", p.getPriority());
    }
}
