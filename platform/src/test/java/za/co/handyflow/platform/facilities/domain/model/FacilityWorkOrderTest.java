package za.co.handyflow.platform.facilities.domain.model;

import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class FacilityWorkOrderTest {

    private FacilityWorkOrder newOrder() {
        return FacilityWorkOrder.create(TenantId.generate(), "WO-00001", UUID.randomUUID(), UUID.randomUUID(),
                null, "CORRECTIVE", "NORMAL", "Leaking tap in kitchen", "Jane Reception", LocalDate.now());
    }

    private FacilityWorkOrder inProgress() {
        FacilityWorkOrder wo = newOrder();
        wo.assign(UUID.randomUUID(), "Sipho Dlamini", null, null);
        wo.start();
        return wo;
    }

    private FacilityWorkOrder request() {
        return FacilityWorkOrder.createRequest(TenantId.generate(), "WO-00010", UUID.randomUUID(), UUID.randomUUID(),
                "CORRECTIVE", "HIGH", "Broken lift", "Reception", UUID.randomUUID(), true, null);
    }

    private void complete(FacilityWorkOrder wo, BigDecimal cost, Instant completedAt) {
        wo.complete("Done", cost, completedAt, UUID.randomUUID(), null, null);
    }

    // ── lifecycle ────────────────────────────────────────────────────────────

    @Test
    void newWorkOrderStartsOpen() {
        FacilityWorkOrder wo = newOrder();
        assertEquals("OPEN", wo.getStatus());
        assertFalse(wo.isTerminal());
    }

    @Test
    void assignRequiresTechnicianOrVendor() {
        FacilityWorkOrder wo = newOrder();
        assertThrows(IllegalArgumentException.class, () -> wo.assign(null, null, null, null));
    }

    @Test
    void fullLifecycleTransitionsCorrectly() {
        FacilityWorkOrder wo = newOrder();
        wo.assign(UUID.randomUUID(), "Sipho Dlamini", null, null);
        assertEquals("ASSIGNED", wo.getStatus());

        wo.start();
        assertEquals("IN_PROGRESS", wo.getStatus());

        wo.putOnHold("Waiting for parts");
        assertEquals("ON_HOLD", wo.getStatus());
        assertEquals("Waiting for parts", wo.getHoldReason());

        wo.start();
        assertEquals("IN_PROGRESS", wo.getStatus());
        assertNull(wo.getHoldReason());

        wo.complete("Replaced washer", new BigDecimal("150.00"), null, UUID.randomUUID(), 45, "Worn washer");
        assertEquals("COMPLETED", wo.getStatus());
        assertTrue(wo.isTerminal());
        assertNotNull(wo.getCompletedAt());
        assertEquals(45, (int) wo.getDowntimeMinutes());
        assertEquals("Worn washer", wo.getFailureCause());
    }

    @Test
    void cannotStartAWorkOrderThatIsStillOpen() {
        assertThrows(IllegalStateException.class, newOrder()::start);
    }

    @Test
    void onlyARunningJobCanBePutOnHold() {
        assertThrows(IllegalStateException.class, () -> newOrder().putOnHold("why"));
    }

    @Test
    void cannotCancelACompletedWorkOrder() {
        FacilityWorkOrder wo = inProgress();
        complete(wo, BigDecimal.TEN, null);
        assertThrows(IllegalStateException.class, () -> wo.cancel("changed my mind"));
    }

    @Test
    void anAssignedJobKeepsItsStatusWhenReassignedMidWay() {
        FacilityWorkOrder wo = inProgress();
        wo.assign(UUID.randomUUID(), "Other Tech", null, null);
        assertEquals("IN_PROGRESS", wo.getStatus());
        assertEquals("Other Tech", wo.getTechnicianName());
    }

    // ── completion details ───────────────────────────────────────────────────

    @Test
    void negativeCostOrDowntimeIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> complete(inProgress(), new BigDecimal("-1"), null));
        assertThrows(IllegalArgumentException.class,
                () -> inProgress().complete("x", null, null, UUID.randomUUID(), -5, null));
    }

    @Test
    void completionTimeCannotBeInTheFuture() {
        assertThrows(IllegalArgumentException.class,
                () -> complete(inProgress(), BigDecimal.ONE, Instant.now().plusSeconds(3600)));
    }

    @Test
    void aSmallClockSkewOnTheTechniciansPhoneIsTolerated() {
        FacilityWorkOrder wo = inProgress();
        complete(wo, BigDecimal.ONE, Instant.now().plusSeconds(120));
        assertEquals("COMPLETED", wo.getStatus());
    }

    @Test
    void completionTimeCannotPrecedeTheWorkOrder() {
        assertThrows(IllegalArgumentException.class,
                () -> complete(inProgress(), BigDecimal.ONE, Instant.now().minusSeconds(86_400)));
    }

    @Test
    void aRejectedCompletionLeavesTheOrderRunning() {
        FacilityWorkOrder wo = inProgress();
        assertThrows(IllegalArgumentException.class, () -> complete(wo, BigDecimal.ONE, Instant.now().plusSeconds(3600)));
        assertEquals("IN_PROGRESS", wo.getStatus());
        assertNull(wo.getCompletedAt());
    }

    // ── requests & approval ──────────────────────────────────────────────────

    @Test
    void aRequestCannotBeAssignedUntilApproved() {
        FacilityWorkOrder wo = request();
        assertEquals("REQUESTED", wo.getStatus());
        assertTrue(wo.isSafetyImpact());
        assertThrows(IllegalStateException.class, () -> wo.assign(UUID.randomUUID(), "T", null, null));
        wo.approve(UUID.randomUUID(), "URGENT", LocalDate.now().plusDays(1), true);
        assertEquals("OPEN", wo.getStatus());
        assertEquals("URGENT", wo.getPriority());
        assertTrue(wo.isRequiresVerification());
        assertNotNull(wo.getApprovedAt());
    }

    @Test
    void aRequestIsDecidedOnlyOnce() {
        FacilityWorkOrder wo = request();
        wo.reject(UUID.randomUUID(), "Not our building");
        assertEquals("REJECTED", wo.getStatus());
        assertEquals("Not our building", wo.getRejectionReason());
        assertThrows(IllegalStateException.class, () -> wo.approve(UUID.randomUUID(), null, null, null));
        assertThrows(IllegalStateException.class, () -> wo.reject(UUID.randomUUID(), "again"));
    }

    @Test
    void rejectingNeedsAReason() {
        assertThrows(IllegalArgumentException.class, () -> request().reject(UUID.randomUUID(), "  "));
    }

    // ── verification & rework ────────────────────────────────────────────────

    @Test
    void verificationJobsWaitForAnotherPersonBeforeCompleting() {
        FacilityWorkOrder wo = inProgress();
        wo.requireVerification(true);
        complete(wo, BigDecimal.TEN, null);
        assertEquals("PENDING_VERIFICATION", wo.getStatus());
        assertFalse(wo.isTerminal());
        assertThrows(IllegalStateException.class, () -> wo.cancel("no"));
        assertThrows(IllegalStateException.class, () -> wo.assign(UUID.randomUUID(), "T", null, null));

        UUID verifier = UUID.randomUUID();
        wo.verify(verifier);
        assertEquals("COMPLETED", wo.getStatus());
        assertNotNull(wo.getVerifiedAt());
    }

    @Test
    void onlyWorkAwaitingVerificationCanBeVerified() {
        assertThrows(IllegalStateException.class, () -> inProgress().verify(UUID.randomUUID()));
    }

    @Test
    void sendingWorkBackCountsReworkAndClearsTheCompletion() {
        FacilityWorkOrder wo = inProgress();
        wo.requireVerification(true);
        complete(wo, BigDecimal.TEN, null);
        wo.rejectVerification("Still leaking");
        assertEquals("IN_PROGRESS", wo.getStatus());
        assertEquals(1, wo.getReworkCount());
        assertEquals("Still leaking", wo.getLastReworkReason());
        assertNull(wo.getCompletedAt());
        complete(wo, BigDecimal.TEN, null);
        wo.rejectVerification("Different leak now");
        assertEquals(2, wo.getReworkCount());
    }

    @Test
    void sendingWorkBackNeedsAReason() {
        FacilityWorkOrder wo = inProgress();
        wo.requireVerification(true);
        complete(wo, BigDecimal.TEN, null);
        assertThrows(IllegalArgumentException.class, () -> wo.rejectVerification(" "));
    }

    // ── overdue ──────────────────────────────────────────────────────────────

    @Test
    void isOverdueOnlyWhenNotFinishedAndPastScheduledDate() {
        FacilityWorkOrder wo = FacilityWorkOrder.create(TenantId.generate(), "WO-00002", UUID.randomUUID(),
                null, null, "CORRECTIVE", "NORMAL", "desc", "reporter", LocalDate.now().minusDays(3));
        assertTrue(wo.isOverdue(LocalDate.now()));
        wo.cancel("no longer needed");
        assertFalse(wo.isOverdue(LocalDate.now()));
    }

    @Test
    void workAwaitingAVerifierIsNotOverdue() {
        FacilityWorkOrder wo = FacilityWorkOrder.create(TenantId.generate(), "WO-00003", UUID.randomUUID(),
                null, null, "CORRECTIVE", "NORMAL", "desc", "reporter", LocalDate.now().minusDays(3));
        wo.requireVerification(true);
        wo.assign(UUID.randomUUID(), "T", null, null);
        wo.start();
        complete(wo, BigDecimal.ONE, null);
        assertEquals("PENDING_VERIFICATION", wo.getStatus());
        assertFalse(wo.isOverdue(LocalDate.now()));
    }

    @Test
    void anUnscheduledJobIsNeverOverdue() {
        FacilityWorkOrder wo = FacilityWorkOrder.create(TenantId.generate(), "WO-00004", UUID.randomUUID(),
                null, null, "CORRECTIVE", "NORMAL", "desc", "reporter", null);
        assertFalse(wo.isOverdue(LocalDate.now()));
    }
}
