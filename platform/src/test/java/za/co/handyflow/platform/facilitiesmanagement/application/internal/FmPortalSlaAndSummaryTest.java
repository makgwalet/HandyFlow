package za.co.handyflow.platform.facilitiesmanagement.application.internal;

import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.facilitiesmanagement.domain.model.FmInvoice;
import za.co.handyflow.platform.facilitiesmanagement.domain.model.FmWorkOrder;
import za.co.handyflow.platform.facilitiesmanagement.dto.FmPortalSummaryResponse;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class FmPortalSlaAndSummaryTest {

    private static final Instant NOW = Instant.parse("2026-10-27T10:00:00Z");

    private FmWorkOrder order(Instant resolutionDue) {
        FmWorkOrder wo = FmWorkOrder.createPortalRequest(TenantId.generate(), "WO-1", UUID.randomUUID(), UUID.randomUUID(), null,
                "CORRECTIVE", "NORMAL", "Aircon not cooling", "Client portal", UUID.randomUUID(), false, null);
        if (resolutionDue != null) wo.applySla(60, 480, "24X7", NOW.plus(Duration.ofHours(1)), resolutionDue);
        return wo;
    }

    private FmWorkOrder approved(Instant resolutionDue) {
        FmWorkOrder wo = order(resolutionDue);
        wo.approve(UUID.randomUUID(), null, null, null);
        return wo;
    }

    private FmInvoice invoice(String total, LocalDate due) {
        return FmInvoice.create(TenantId.generate(), UUID.randomUUID(), "FMINV-1", due.minusDays(30), due.minusDays(1),
                due.minusDays(14), due, new BigDecimal(total), BigDecimal.ZERO, BigDecimal.ZERO, UUID.randomUUID(), null);
    }

    // ── SLA visibility ───────────────────────────────────────────────────────

    @Test
    void noSlaIsShownUntilStaffHaveApproved() {
        FmWorkOrder wo = order(NOW.plus(Duration.ofHours(8)));
        assertNull(FmPortalSla.dueAt(wo));
        assertNull(FmPortalSla.state(wo, NOW));
    }

    @Test
    void anApprovedJobShowsItsFixByTimeAndState() {
        Instant due = NOW.plus(Duration.ofHours(8));
        FmWorkOrder wo = approved(due);
        assertEquals(due, FmPortalSla.dueAt(wo));
        assertEquals("ON_TRACK", FmPortalSla.state(wo, NOW));
        assertEquals("AT_RISK", FmPortalSla.state(wo, due.minus(Duration.ofMinutes(20))));
        assertEquals("BREACHED", FmPortalSla.state(wo, due.plusSeconds(1)));
    }

    @Test
    void jobsWithoutATargetShowNothing() {
        assertNull(FmPortalSla.state(approved(null), NOW));
    }

    @Test
    void withdrawnAndDeclinedRequestsShowNothing() {
        FmWorkOrder withdrawn = order(NOW.plus(Duration.ofHours(8)));
        withdrawn.cancelByClient(withdrawn.getRequestedByPortalUserId());
        assertNull(FmPortalSla.state(withdrawn, NOW));
        FmWorkOrder declined = order(NOW.plus(Duration.ofHours(8)));
        declined.reject(UUID.randomUUID(), "no", null);
        assertNull(FmPortalSla.dueAt(declined));
    }

    // ── summary ──────────────────────────────────────────────────────────────

    @Test
    void workIsCountedUnderThePortalsOwnStatusNames() {
        Map<String, Long> raw = new LinkedHashMap<>();
        raw.put("REQUESTED", 2L);
        raw.put("IN_PROGRESS", 3L);
        raw.put("PENDING_VERIFICATION", 1L); // shown to clients as in progress
        raw.put("COMPLETED", 10L);
        raw.put("REJECTED", 1L);
        raw.put("CANCELLED", 4L);
        FmPortalSummaryResponse s = FmPortalSummary.build(raw, List.of(), List.of(), NOW, LocalDate.of(2026, 10, 27));
        assertEquals(2L, (long) s.workOrders().get("SUBMITTED"));
        assertEquals(4L, (long) s.workOrders().get("IN_PROGRESS"));
        assertEquals(1L, (long) s.workOrders().get("DECLINED"));
        assertEquals(6L, s.openWork()); // submitted 2 + in progress 4
    }

    @Test
    void slaRiskCountsOnlyUnfinishedWork() {
        FmWorkOrder risky = approved(NOW.plus(Duration.ofMinutes(10)));
        FmWorkOrder late = approved(NOW.minus(Duration.ofHours(1)));
        FmWorkOrder fine = approved(NOW.plus(Duration.ofHours(30)));
        FmPortalSummaryResponse s = FmPortalSummary.build(Map.of(), List.of(risky, late, fine), List.of(), NOW, LocalDate.of(2026, 10, 27));
        assertEquals(1, s.slaAtRisk());
        assertEquals(1, s.slaBreached());
    }

    @Test
    void outstandingAndOverdueMoneyIgnoresSettledInvoices() {
        LocalDate today = LocalDate.of(2026, 10, 27);
        FmInvoice overdue = invoice("1000.00", today.minusDays(5));
        FmInvoice current = invoice("500.00", today.plusDays(10));
        FmInvoice dueToday = invoice("200.00", today);
        FmInvoice zero = invoice("0.00", today.minusDays(40));
        FmPortalSummaryResponse s = FmPortalSummary.build(Map.of(), List.of(), List.of(overdue, current, dueToday, zero), NOW, today);
        assertEquals(0, new BigDecimal("1700.00").compareTo(s.outstandingBalance()));
        assertEquals(1, s.overdueInvoices());
        assertEquals(0, new BigDecimal("1000.00").compareTo(s.overdueBalance()));
    }

    @Test
    void anEmptyClientGetsAllZeros() {
        FmPortalSummaryResponse s = FmPortalSummary.build(Map.of(), List.of(), List.of(), NOW, LocalDate.of(2026, 10, 27));
        assertTrue(s.workOrders().isEmpty());
        assertEquals(0, s.openWork());
        assertEquals(0, BigDecimal.ZERO.compareTo(s.outstandingBalance()));
    }
}
