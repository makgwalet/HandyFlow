package za.co.handyflow.platform.facilitiesmanagement.application.internal;

import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.facilitiesmanagement.domain.model.FmWorkOrder;
import za.co.handyflow.platform.facilitiesmanagement.domain.repository.FmWorkOrderRepository;
import za.co.handyflow.platform.notifications.domain.model.NotificationType;
import za.co.handyflow.platform.shared.SchedulerLease;
import za.co.handyflow.platform.shared.TenantId;

import java.lang.reflect.Proxy;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/** The SLA sweep: warn once, breach once, right notification types, one bad row never stops the sweep. */
class FmSlaMonitorTest {

    private final TenantId tenant = TenantId.generate();
    private final List<FmWorkOrder> open = new ArrayList<>();
    private final List<NotificationType> sent = new ArrayList<>();
    private int saves = 0;
    private boolean failOnFirstAlert = false;

    private final FmWorkOrderRepository repo = (FmWorkOrderRepository) Proxy.newProxyInstance(
            getClass().getClassLoader(), new Class<?>[]{FmWorkOrderRepository.class}, (p, m, a) -> {
                switch (m.getName()) {
                    // Mirror the real queries: unmet and unbreached targets only.
                    case "findOpenForResponseSla":
                        return open.stream().filter(w -> w.getRespondedAt() == null && w.getResponseBreachedAt() == null).toList();
                    case "findOpenForResolutionSla":
                        return open.stream().filter(w -> w.getCompletedAt() == null && w.getResolutionBreachedAt() == null).toList();
                    case "save": saves++; return a[0];
                    default: throw new UnsupportedOperationException(m.getName());
                }
            });

    private final FmAlerts alerts = new FmAlerts(null, null) {
        @Override public void toAdmins(TenantId t, NotificationType type, String title, String message, String url, String id) {
            if (failOnFirstAlert) { failOnFirstAlert = false; throw new RuntimeException("mail down"); }
            sent.add(type);
        }
    };

    private final FmSlaMonitor monitor = new FmSlaMonitor(repo, alerts, new SchedulerLease(null) {
        @Override public boolean runExclusively(String n, Duration d, Runnable r) { r.run(); return true; }
    });

    /** Response due 60 min and resolution due 240 min after creation. */
    private FmWorkOrder slaOrder() {
        FmWorkOrder wo = FmWorkOrder.create(tenant, "WO-" + (open.size() + 1), UUID.randomUUID(), UUID.randomUUID(), null, null,
                "CORRECTIVE", "URGENT", "Leak", "Reception", LocalDate.now());
        Instant c = wo.getCreatedAt();
        wo.applySla(60, 240, "24X7", c.plus(Duration.ofMinutes(60)), c.plus(Duration.ofMinutes(240)));
        open.add(wo);
        return wo;
    }

    private static Instant at(FmWorkOrder wo, int minutesAfterCreation) {
        return wo.getCreatedAt().plus(Duration.ofMinutes(minutesAfterCreation));
    }

    @Test
    void nothingHappensWhileTheTargetIsComfortablyOnTrack() {
        FmWorkOrder wo = slaOrder();
        assertEquals(0, monitor.checkResponse(at(wo, 10)));
        assertEquals(0, monitor.checkResolution(at(wo, 10)));
        assertTrue(sent.isEmpty());
        assertEquals(0, saves);
    }

    @Test
    void enteringTheWarningWindowRaisesAnAtRiskAlertExactlyOnce() {
        FmWorkOrder wo = slaOrder(); // response warn window = last 30 minutes (floor), so from minute 30
        assertEquals(1, monitor.checkResponse(at(wo, 35)));
        assertEquals(0, monitor.checkResponse(at(wo, 40)));
        assertEquals(List.of(NotificationType.FM_SLA_AT_RISK), sent);
        assertNotNull(wo.getResponseWarnedAt());
    }

    @Test
    void missingTheTargetRaisesABreachAlertOnceAndRecordsIt() {
        FmWorkOrder wo = slaOrder();
        assertEquals(1, monitor.checkResponse(at(wo, 61)));
        assertEquals(0, monitor.checkResponse(at(wo, 90)));
        assertEquals(List.of(NotificationType.FM_SLA_BREACHED), sent);
        assertNotNull(wo.getResponseBreachedAt());
    }

    @Test
    void aTargetThatGoesStraightToBreachDoesNotAlsoSendAWarning() {
        FmWorkOrder wo = slaOrder();
        monitor.checkResponse(at(wo, 120));
        monitor.checkResponse(at(wo, 130));
        assertEquals(List.of(NotificationType.FM_SLA_BREACHED), sent);
    }

    @Test
    void responseAndResolutionAreTrackedIndependently() {
        FmWorkOrder wo = slaOrder();
        monitor.checkResponse(at(wo, 61));
        monitor.checkResolution(at(wo, 61));
        assertEquals(List.of(NotificationType.FM_SLA_BREACHED), sent);
        monitor.checkResolution(at(wo, 241));
        assertEquals(List.of(NotificationType.FM_SLA_BREACHED, NotificationType.FM_SLA_BREACHED), sent);
        assertNotNull(wo.getResolutionBreachedAt());
    }

    @Test
    void aFailingAlertForOneOrderDoesNotStopTheSweep() {
        FmWorkOrder first = slaOrder();
        FmWorkOrder second = slaOrder();
        failOnFirstAlert = true;
        int raised = monitor.checkResponse(at(first, 61));
        assertEquals(1, raised); // the second order still alerted
        assertEquals(1, sent.size());
        assertNotNull(second.getResponseBreachedAt());
    }

    @Test
    void theScheduledRunSweepsBothClocks() {
        FmWorkOrder wo = slaOrder();
        wo.markResponseBreached(Instant.now()); // already handled
        monitor.run(); // nothing is due yet "now" — must simply not blow up
        assertTrue(sent.isEmpty());
    }
}
