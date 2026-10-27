package za.co.handyflow.platform.facilitiesmanagement.domain.model;

import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class FmWorkOrderTest {

    private FmWorkOrder newOrder() {
        return FmWorkOrder.create(TenantId.generate(), "WO-00001", UUID.randomUUID(), UUID.randomUUID(),
                null, null, "CORRECTIVE", "NORMAL", "Leaking tap in kitchen", "Jane Reception", LocalDate.now());
    }

    private FmWorkOrder inProgress() {
        FmWorkOrder wo = newOrder();
        wo.assign(UUID.randomUUID(), "Sipho Dlamini", null, null);
        wo.start();
        return wo;
    }

    private void complete(FmWorkOrder wo, BigDecimal cost) {
        wo.complete("Done", cost, cost, null, UUID.randomUUID(), null, null);
    }

    // ── lifecycle ────────────────────────────────────────────────────────────

    @Test
    void newWorkOrderStartsOpen() {
        FmWorkOrder wo = newOrder();
        assertEquals("OPEN", wo.getStatus());
        assertFalse(wo.isTerminal());
        assertFalse(wo.isInvoiced());
        assertFalse(wo.hasSla());
    }

    @Test
    void aRequestStartsRequestedAndCannotBeAssignedUntilApproved() {
        FmWorkOrder wo = FmWorkOrder.createRequest(TenantId.generate(), "WO-00009", UUID.randomUUID(), UUID.randomUUID(),
                null, "CORRECTIVE", "HIGH", "Broken lift", "Reception", UUID.randomUUID(), true, null);
        assertEquals("REQUESTED", wo.getStatus());
        assertThrows(IllegalStateException.class, () -> wo.assign(UUID.randomUUID(), "T", null, null));
        wo.approve(UUID.randomUUID(), "URGENT", LocalDate.now(), true);
        assertEquals("OPEN", wo.getStatus());
        assertEquals("URGENT", wo.getPriority());
        assertTrue(wo.isRequiresVerification());
    }

    @Test
    void aRequestCanOnlyBeApprovedOrRejectedOnce() {
        FmWorkOrder wo = FmWorkOrder.createRequest(TenantId.generate(), "WO-00010", UUID.randomUUID(), UUID.randomUUID(),
                null, "CORRECTIVE", "NORMAL", "d", "r", UUID.randomUUID(), false, null);
        wo.reject(UUID.randomUUID(), "Not our scope");
        assertEquals("REJECTED", wo.getStatus());
        assertThrows(IllegalStateException.class, () -> wo.approve(UUID.randomUUID(), null, null, null));
        assertThrows(IllegalStateException.class, () -> wo.reject(UUID.randomUUID(), "again"));
    }

    @Test
    void assignRequiresTechnicianOrVendor() {
        FmWorkOrder wo = newOrder();
        assertThrows(IllegalArgumentException.class, () -> wo.assign(null, null, null, null));
    }

    @Test
    void assignToVendorAloneIsValid() {
        FmWorkOrder wo = newOrder();
        wo.assign(null, null, UUID.randomUUID(), "Acme Elevators (Pty) Ltd");
        assertEquals("ASSIGNED", wo.getStatus());
        assertNull(wo.getTechnicianId());
        assertEquals("Acme Elevators (Pty) Ltd", wo.getVendorName());
    }

    @Test
    void theFirstAssignmentIsTheResponseAndLaterOnesDoNotMoveIt() throws Exception {
        FmWorkOrder wo = newOrder();
        assertNull(wo.getRespondedAt());
        wo.assign(UUID.randomUUID(), "First", null, null);
        Instant first = wo.getRespondedAt();
        assertNotNull(first);
        Thread.sleep(5);
        wo.assign(UUID.randomUUID(), "Second", null, null);
        assertEquals(first, wo.getRespondedAt());
    }

    @Test
    void fullLifecycleTransitionsCorrectly() {
        FmWorkOrder wo = newOrder();
        wo.assign(UUID.randomUUID(), "Sipho Dlamini", null, null);
        assertEquals("ASSIGNED", wo.getStatus());
        wo.start();
        assertEquals("IN_PROGRESS", wo.getStatus());
        wo.putOnHold("Waiting for parts", false);
        assertEquals("ON_HOLD", wo.getStatus());
        wo.start();
        assertEquals("IN_PROGRESS", wo.getStatus());

        wo.complete("Replaced washer", new BigDecimal("150.00"), new BigDecimal("90.00"), null, UUID.randomUUID(), 30, "Worn washer");
        assertEquals("COMPLETED", wo.getStatus());
        assertTrue(wo.isTerminal());
        assertNotNull(wo.getCompletedAt());
        assertTrue(wo.isBillable());
    }

    @Test
    void cannotStartAWorkOrderThatIsStillOpen() {
        assertThrows(IllegalStateException.class, newOrder()::start);
    }

    // ── completion ───────────────────────────────────────────────────────────

    @Test
    void aCompletionTimeInTheFutureIsRejected() {
        FmWorkOrder wo = inProgress();
        assertThrows(IllegalArgumentException.class, () -> wo.complete("Done", BigDecimal.TEN, BigDecimal.TEN,
                Instant.now().plusSeconds(3600), UUID.randomUUID(), null, null));
        assertEquals("IN_PROGRESS", wo.getStatus());
    }

    @Test
    void aCompletionTimeBeforeTheWorkOrderExistedIsRejected() {
        FmWorkOrder wo = inProgress();
        assertThrows(IllegalArgumentException.class, () -> wo.complete("Done", BigDecimal.TEN, BigDecimal.TEN,
                wo.getCreatedAt().minusSeconds(60), UUID.randomUUID(), null, null));
    }

    @Test
    void aSlightlyAheadDeviceClockIsTolerated() {
        FmWorkOrder wo = inProgress();
        wo.complete("Done", BigDecimal.TEN, BigDecimal.TEN, Instant.now().plusSeconds(60), UUID.randomUUID(), null, null);
        assertEquals("COMPLETED", wo.getStatus());
    }

    @Test
    void aCompletionTimeSinceTheWorkOrderWasRaisedIsKept() {
        FmWorkOrder wo = inProgress();
        Instant when = wo.getCreatedAt().plusSeconds(1);
        wo.complete("Done", BigDecimal.TEN, BigDecimal.TEN, when, UUID.randomUUID(), null, null);
        assertEquals(when, wo.getCompletedAt());
    }

    @Test
    void completionRejectsNegativeFigures() {
        assertThrows(IllegalArgumentException.class, () -> inProgress().complete("Done", new BigDecimal("-1"), null, null, UUID.randomUUID(), null, null));
        assertThrows(IllegalArgumentException.class, () -> inProgress().complete("Done", null, new BigDecimal("-1"), null, UUID.randomUUID(), null, null));
        assertThrows(IllegalArgumentException.class, () -> inProgress().complete("Done", null, null, null, UUID.randomUUID(), -5, null));
    }

    @Test
    void cannotCompleteAWorkOrderThatIsNotBeingWorkedOn() {
        assertThrows(IllegalStateException.class, () -> complete(newOrder(), BigDecimal.TEN));
    }

    @Test
    void verificationSendsCompletionToPendingVerificationFirst() {
        FmWorkOrder wo = newOrder();
        wo.requireVerification(true);
        wo.assign(UUID.randomUUID(), "Sipho", null, null);
        wo.start();
        complete(wo, BigDecimal.TEN);
        assertEquals("PENDING_VERIFICATION", wo.getStatus());
        assertFalse(wo.isBillable()); // not billable until verified
        UUID verifier = UUID.randomUUID();
        wo.verify(verifier);
        assertEquals("COMPLETED", wo.getStatus());
        assertEquals(verifier, wo.getVerifiedByUserId());
        assertTrue(wo.isBillable());
    }

    @Test
    void reworkSendsTheJobBackAndClearsTheCompletion() {
        FmWorkOrder wo = newOrder();
        wo.requireVerification(true);
        wo.assign(UUID.randomUUID(), "Sipho", null, null);
        wo.start();
        complete(wo, BigDecimal.TEN);
        assertThrows(IllegalArgumentException.class, () -> wo.rejectVerification(" "));
        wo.rejectVerification("Still leaking");
        assertEquals("IN_PROGRESS", wo.getStatus());
        assertNull(wo.getCompletedAt());
        assertEquals(1, wo.getReworkCount());
        assertEquals("Still leaking", wo.getLastReworkReason());
    }

    @Test
    void onlyAJobAwaitingVerificationCanBeVerifiedOrSentBack() {
        FmWorkOrder wo = inProgress();
        assertThrows(IllegalStateException.class, () -> wo.verify(UUID.randomUUID()));
        assertThrows(IllegalStateException.class, () -> wo.rejectVerification("x"));
    }

    // ── cancel / invoice / billable ──────────────────────────────────────────

    @Test
    void cannotCancelACompletedInvoicedWorkOrder() {
        FmWorkOrder wo = inProgress();
        complete(wo, BigDecimal.TEN);
        wo.markInvoiced(UUID.randomUUID());
        assertTrue(wo.isInvoiced());
        assertThrows(IllegalStateException.class, () -> wo.cancel("changed my mind"));
    }

    @Test
    void cannotCancelACompletedWorkOrderEvenBeforeInvoicing() {
        FmWorkOrder wo = inProgress();
        complete(wo, BigDecimal.TEN);
        assertThrows(IllegalStateException.class, () -> wo.cancel("changed my mind"));
    }

    @Test
    void workAwaitingVerificationCannotBeCancelled() {
        FmWorkOrder wo = newOrder();
        wo.requireVerification(true);
        wo.assign(UUID.randomUUID(), "Sipho", null, null);
        wo.start();
        complete(wo, BigDecimal.TEN);
        assertThrows(IllegalStateException.class, () -> wo.cancel("nope"));
    }

    @Test
    void isBillableOnlyWhenCompletedNotInvoicedAndPositiveCost() {
        FmWorkOrder zero = inProgress();
        complete(zero, BigDecimal.ZERO);
        assertFalse(zero.isBillable());

        FmWorkOrder wo2 = inProgress();
        complete(wo2, BigDecimal.TEN);
        assertTrue(wo2.isBillable());
        UUID invoice = UUID.randomUUID();
        wo2.markInvoiced(invoice);
        assertEquals(invoice, wo2.getInvoiceId());
        assertFalse(wo2.isBillable());
        wo2.releaseFromInvoice();
        assertTrue(wo2.isBillable());
        assertNull(wo2.getInvoiceId());
    }

    @Test
    void isOverdueOnlyWhenNotTerminalAndPastScheduledDate() {
        FmWorkOrder wo = FmWorkOrder.create(TenantId.generate(), "WO-00002", UUID.randomUUID(), UUID.randomUUID(),
                null, null, "CORRECTIVE", "NORMAL", "desc", "reporter", LocalDate.now().minusDays(3));
        assertTrue(wo.isOverdue(LocalDate.now()));
        wo.cancel("no longer needed");
        assertFalse(wo.isOverdue(LocalDate.now()));
    }

    // ── contract & SLA ───────────────────────────────────────────────────────

    @Test
    void contractTermsAreStampedOnTheWorkOrder() {
        FmWorkOrder wo = newOrder();
        UUID agreement = UUID.randomUUID();
        wo.applyContract(agreement, "INCLUDED", new BigDecimal("450.00"), new BigDecimal("12.50"));
        assertEquals(agreement, wo.getAgreementId());
        assertEquals("INCLUDED", wo.getCoverage());
        assertEquals(new BigDecimal("450.00"), wo.getLabourRate());
        assertEquals(new BigDecimal("12.50"), wo.getPartsMarkupPercent());
    }

    @Test
    void holdingWithPausedSlaNeedsAnSlaToPause() {
        FmWorkOrder noSla = inProgress();
        assertThrows(IllegalStateException.class, () -> noSla.putOnHold("Waiting for access", true));

        FmWorkOrder wo = inProgress();
        Instant now = Instant.now();
        wo.applySla(60, 240, "24X7", now.plusSeconds(3600), now.plusSeconds(14400));
        assertTrue(wo.hasSla());
        wo.putOnHold("Waiting for access", true);
        assertNotNull(wo.getSlaPausedAt());
        assertEquals("ON_HOLD", wo.getStatus());
    }

    @Test
    void holdingWithoutPausingLeavesTheClockRunning() {
        FmWorkOrder wo = inProgress();
        Instant now = Instant.now();
        wo.applySla(60, 240, "24X7", now.plusSeconds(3600), now.plusSeconds(14400));
        wo.putOnHold("Parts", false);
        assertNull(wo.getSlaPausedAt());
    }

    @Test
    void resumingMovesTheDeadlineOutAndAccumulatesPausedTime() {
        FmWorkOrder wo = inProgress();
        Instant now = Instant.now();
        wo.applySla(60, 240, "24X7", now.plusSeconds(3600), now.plusSeconds(14400));
        wo.putOnHold("Access", true);
        Instant newDue = now.plusSeconds(14400 + 1800);
        wo.resumeSla(30, newDue);
        assertNull(wo.getSlaPausedAt());
        assertEquals(30, wo.getSlaPausedMinutes());
        assertEquals(newDue, wo.getResolutionDueAt());
        wo.start();
        wo.putOnHold("Access again", true);
        wo.resumeSla(15, newDue.plusSeconds(900));
        assertEquals(45, wo.getSlaPausedMinutes());
    }

    @Test
    void warnedAndBreachedTimestampsAreSetOnceAndBreachImpliesWarned() {
        FmWorkOrder wo = newOrder();
        Instant t1 = Instant.parse("2026-11-02T08:00:00Z");
        Instant t2 = Instant.parse("2026-11-02T09:00:00Z");
        wo.markResponseWarned(t1);
        wo.markResponseWarned(t2);
        assertEquals(t1, wo.getResponseWarnedAt());
        wo.markResponseBreached(t2);
        wo.markResponseBreached(t1);
        assertEquals(t2, wo.getResponseBreachedAt());

        wo.markResolutionBreached(t1);
        assertEquals(t1, wo.getResolutionBreachedAt());
        assertEquals(t1, wo.getResolutionWarnedAt()); // a breach that was never warned about is also marked warned
    }
}
