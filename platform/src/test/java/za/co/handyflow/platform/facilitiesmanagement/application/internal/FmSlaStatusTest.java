package za.co.handyflow.platform.facilitiesmanagement.application.internal;

import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.facilitiesmanagement.domain.model.FmWorkOrder;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class FmSlaStatusTest {

    private FmWorkOrder order() {
        return FmWorkOrder.create(TenantId.generate(), "WO-1", UUID.randomUUID(), UUID.randomUUID(), null, null,
                "CORRECTIVE", "HIGH", "Broken door", "Reception", LocalDate.now());
    }

    /** A work order with an SLA whose targets are the given minutes after it was raised. */
    private FmWorkOrder withSla(long responseMin, long resolutionMin) {
        FmWorkOrder wo = order();
        Instant created = wo.getCreatedAt();
        wo.applySla((int) responseMin, (int) resolutionMin, "24X7",
                created.plus(Duration.ofMinutes(responseMin)), created.plus(Duration.ofMinutes(resolutionMin)));
        return wo;
    }

    private static Instant after(FmWorkOrder wo, long minutes) {
        return wo.getCreatedAt().plus(Duration.ofMinutes(minutes));
    }

    // ── response ─────────────────────────────────────────────────────────────

    @Test
    void noSlaMeansNone() {
        FmWorkOrder wo = order();
        assertEquals("NONE", FmSlaStatus.response(wo, Instant.now()));
        assertEquals("NONE", FmSlaStatus.resolution(wo, Instant.now()));
    }

    @Test
    void anOpenResponseIsOnTrackThenAtRiskThenBreached() {
        FmWorkOrder wo = withSla(120, 480); // warn window = max(30, 24) = 30 minutes before due
        assertEquals("ON_TRACK", FmSlaStatus.response(wo, after(wo, 10)));
        assertEquals("ON_TRACK", FmSlaStatus.response(wo, after(wo, 89)));
        assertEquals("AT_RISK", FmSlaStatus.response(wo, after(wo, 90)));
        assertEquals("AT_RISK", FmSlaStatus.response(wo, after(wo, 120)));
        assertEquals("BREACHED", FmSlaStatus.response(wo, after(wo, 121)));
    }

    @Test
    void assigningInTimeMeetsTheResponseTarget() {
        FmWorkOrder wo = withSla(120, 480);
        wo.assign(UUID.randomUUID(), "Sipho", null, null);
        assertEquals("MET", FmSlaStatus.response(wo, after(wo, 500)));
    }

    @Test
    void assigningLateBreachesTheResponseTargetForGood() {
        FmWorkOrder wo = order();
        Instant created = wo.getCreatedAt();
        wo.applySla(1, 10, "24X7", created.minusSeconds(60), created.plusSeconds(600)); // response was due a minute ago
        wo.assign(UUID.randomUUID(), "Sipho", null, null);
        assertEquals("BREACHED", FmSlaStatus.response(wo, created));
    }

    @Test
    void aCancelledOrRejectedJobHasNoOpenSla() {
        FmWorkOrder wo = withSla(120, 480);
        wo.cancel("not needed");
        assertEquals("NONE", FmSlaStatus.response(wo, after(wo, 500)));
        assertEquals("NONE", FmSlaStatus.resolution(wo, after(wo, 500)));
    }

    // ── resolution ───────────────────────────────────────────────────────────

    @Test
    void anOpenResolutionGoesThroughTheSameStates() {
        FmWorkOrder wo = withSla(60, 600); // warn window = max(30, 120) = 120 minutes before due
        assertEquals("ON_TRACK", FmSlaStatus.resolution(wo, after(wo, 400)));
        assertEquals("AT_RISK", FmSlaStatus.resolution(wo, after(wo, 480)));
        assertEquals("BREACHED", FmSlaStatus.resolution(wo, after(wo, 601)));
    }

    @Test
    void completingBeforeTheDueTimeMeetsResolution() {
        FmWorkOrder wo = withSla(60, 600);
        wo.assign(UUID.randomUUID(), "Sipho", null, null);
        wo.start();
        wo.complete("Done", BigDecimal.TEN, BigDecimal.TEN, null, UUID.randomUUID(), null, null);
        assertEquals("MET", FmSlaStatus.resolution(wo, after(wo, 5000)));
    }

    @Test
    void completingAfterTheDueTimeBreachesResolution() {
        FmWorkOrder wo = order();
        Instant created = wo.getCreatedAt();
        wo.applySla(1, 10, "24X7", created.plusSeconds(60), created.minusSeconds(60)); // resolution was due a minute ago
        wo.assign(UUID.randomUUID(), "Sipho", null, null);
        wo.start();
        wo.complete("Done", BigDecimal.TEN, BigDecimal.TEN, null, UUID.randomUUID(), null, null);
        assertEquals("BREACHED", FmSlaStatus.resolution(wo, created));
    }

    @Test
    void aPausedClockReadsPausedNotBreached() {
        FmWorkOrder wo = withSla(60, 600);
        wo.assign(UUID.randomUUID(), "Sipho", null, null);
        wo.start();
        wo.putOnHold("Waiting for access", true);
        assertEquals("PAUSED", FmSlaStatus.resolution(wo, after(wo, 9999)));
    }

    @Test
    void sendingWorkBackForReworkReopensTheResolutionClock() {
        FmWorkOrder wo = withSla(60, 600);
        wo.requireVerification(true);
        wo.assign(UUID.randomUUID(), "Sipho", null, null);
        wo.start();
        wo.complete("Done", BigDecimal.TEN, BigDecimal.TEN, null, UUID.randomUUID(), null, null);
        wo.rejectVerification("Still broken");
        assertEquals("BREACHED", FmSlaStatus.resolution(wo, after(wo, 700)));
    }

    // ── warn window ──────────────────────────────────────────────────────────

    @Test
    void theWarningWindowIsTwentyPercentOfTheTargetButNeverUnderThirtyMinutes() {
        Instant start = Instant.parse("2026-11-02T06:00:00Z");
        assertEquals(start.plus(Duration.ofMinutes(60 - 30)), FmSlaStatus.warnAt(start, start.plus(Duration.ofMinutes(60))));    // 20% = 12 -> 30
        assertEquals(start.plus(Duration.ofMinutes(480 - 96)), FmSlaStatus.warnAt(start, start.plus(Duration.ofMinutes(480))));  // 20% = 96
        assertEquals(start.plus(Duration.ofMinutes(2880 - 576)), FmSlaStatus.warnAt(start, start.plus(Duration.ofMinutes(2880)))); // 20% = 576
    }

    @Test
    void aShortTargetWarnsFromTheStart() {
        Instant start = Instant.parse("2026-11-02T06:00:00Z");
        Instant due = start.plus(Duration.ofMinutes(20));
        assertTrue(FmSlaStatus.warnAt(start, due).isBefore(start)); // 30-minute window is wider than the 20-minute target
    }
}
