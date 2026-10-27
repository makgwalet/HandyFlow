package za.co.handyflow.platform.facilitiesmanagement.application.internal;

import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.facilitiesmanagement.domain.model.FmQuote;
import za.co.handyflow.platform.facilitiesmanagement.domain.repository.FmQuoteRepository;
import za.co.handyflow.platform.notifications.domain.model.NotificationType;
import za.co.handyflow.platform.shared.SchedulerLease;
import za.co.handyflow.platform.shared.TenantId;

import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/** The daily sweep records unanswered, out-of-date quotes as EXPIRED, audits and alerts, and survives one failure. */
class FmQuoteExpiryJobTest {

    private final TenantId tenant = TenantId.generate();
    private final LocalDate today = LocalDate.of(2026, 10, 27);
    private final List<FmQuote> due = new ArrayList<>();
    private final List<String> audit = new ArrayList<>();
    private final List<NotificationType> alertsSent = new ArrayList<>();
    private UUID failOn;

    private final FmQuoteRepository repo = (FmQuoteRepository) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{FmQuoteRepository.class}, (p, m, a) -> switch (m.getName()) {
        case "findSentExpiredAcrossTenants" -> due;
        case "save" -> { if (((FmQuote) a[0]).getId().equals(failOn)) throw new IllegalStateException("db"); yield a[0]; }
        default -> throw new UnsupportedOperationException(m.getName());
    });
    private final FmAuditService auditService = new FmAuditService(null) {
        @Override public void recordSystem(TenantId t, String action, String type, UUID id, String details) { audit.add(action); }
    };
    private final FmAlerts alerts = new FmAlerts(null, null) {
        @Override public void toAdmins(TenantId t, NotificationType type, String title, String message, String url, String id) { alertsSent.add(type); }
    };
    private final FmQuoteExpiryJob job = new FmQuoteExpiryJob(repo, auditService, alerts, new SchedulerLease(null), new FmSyncExecutor());

    private FmQuote overdue() {
        FmQuote q = FmQuote.create(tenant, "FMQ-1", UUID.randomUUID(), UUID.randomUUID(), null, null, "Pump", null, today.minusDays(1), null, UUID.randomUUID());
        q.setAmounts(BigDecimal.TEN, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.TEN);
        q.send(UUID.randomUUID(), today.minusDays(5));
        due.add(q);
        return q;
    }

    @Test
    void expiresAuditsAndAlertsEachDueQuote() {
        FmQuote a = overdue(); overdue();
        assertEquals(2, job.sweep(today));
        assertEquals("EXPIRED", a.getStatus());
        assertEquals(List.of("QUOTE_EXPIRED", "QUOTE_EXPIRED"), audit);
        assertEquals(List.of(NotificationType.FM_QUOTE_EXPIRED, NotificationType.FM_QUOTE_EXPIRED), alertsSent);
    }

    @Test
    void oneFailureDoesNotStopTheRest() {
        FmQuote bad = overdue(); FmQuote good = overdue();
        failOn = bad.getId();
        assertEquals(1, job.sweep(today));
        assertEquals(1, alertsSent.size());
        assertEquals("EXPIRED", good.getStatus());
    }

    @Test
    void nothingDueDoesNothing() {
        assertEquals(0, job.sweep(today));
        assertTrue(audit.isEmpty());
    }
}
