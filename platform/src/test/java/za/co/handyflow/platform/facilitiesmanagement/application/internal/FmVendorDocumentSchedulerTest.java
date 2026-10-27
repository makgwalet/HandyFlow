package za.co.handyflow.platform.facilitiesmanagement.application.internal;

import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.facilitiesmanagement.domain.model.FmVendor;
import za.co.handyflow.platform.facilitiesmanagement.domain.model.FmVendorDocument;
import za.co.handyflow.platform.facilitiesmanagement.domain.repository.FmVendorDocumentRepository;
import za.co.handyflow.platform.facilitiesmanagement.domain.repository.FmVendorRepository;
import za.co.handyflow.platform.notifications.domain.model.NotificationType;
import za.co.handyflow.platform.shared.TenantId;

import java.lang.reflect.Proxy;
import java.time.LocalDate;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class FmVendorDocumentSchedulerTest {

    private final TenantId tenant = TenantId.generate();
    private final LocalDate today = LocalDate.of(2026, 10, 27);
    private final FmVendor vendor = FmVendor.create(tenant, "ACME Plumbing", "PLUMBING", null, null, null, null);
    private final List<FmVendorDocument> docs = new ArrayList<>();
    private final List<NotificationType> sent = new ArrayList<>();
    private boolean alertsDown;

    private final FmVendorDocumentRepository docRepo = (FmVendorDocumentRepository) Proxy.newProxyInstance(
            getClass().getClassLoader(), new Class<?>[]{FmVendorDocumentRepository.class}, (p, m, a) -> {
                if ("findValidExpiringAcrossTenants".equals(m.getName())) return List.copyOf(docs);
                throw new UnsupportedOperationException(m.getName());
            });
    private final FmVendorRepository vendorRepo = (FmVendorRepository) Proxy.newProxyInstance(
            getClass().getClassLoader(), new Class<?>[]{FmVendorRepository.class}, (p, m, a) -> {
                if ("findActiveById".equals(m.getName())) return Optional.of(vendor);
                throw new UnsupportedOperationException(m.getName());
            });
    private final FmAlerts alerts = new FmAlerts(null, null) {
        @Override public void toAdmins(TenantId t, NotificationType type, String title, String message, String url, String id) {
            if (alertsDown) throw new IllegalStateException("down");
            sent.add(type);
        }
    };
    private final FmVendorDocumentScheduler scheduler = new FmVendorDocumentScheduler(docRepo, vendorRepo, alerts, null);

    private void doc(int expiresInDays) {
        docs.add(FmVendorDocument.create(tenant, vendor.getId(), "COIDA_LETTER", "C1", null, null, today.plusDays(expiresInDays), null, null));
    }

    @Test
    void remindersFireOnMilestoneDaysOnly() {
        for (int d : new int[]{30, 14, 7, 3, 1, 0}) doc(d);
        for (int d : new int[]{29, 20, 10, 2}) doc(d);
        assertEquals(6, scheduler.sweep(today));
        assertTrue(sent.stream().allMatch(t -> t == NotificationType.FM_VENDOR_DOC_EXPIRING));
    }

    @Test
    void aDocumentThatLapsedYesterdayRaisesTheCriticalAlertOnceAndThenStaysQuiet() {
        doc(-1);
        assertEquals(1, scheduler.sweep(today));
        assertEquals(List.of(NotificationType.FM_VENDOR_DOC_EXPIRED), sent);
        sent.clear();
        docs.clear();
        doc(-2);
        doc(-30);
        assertEquals(0, scheduler.sweep(today));
    }

    @Test
    void anAlertFailureDoesNotStopTheSweep() {
        doc(7);
        doc(3);
        alertsDown = true;
        assertEquals(0, scheduler.sweep(today));   // the failure is logged per document, never thrown
        alertsDown = false;
        assertEquals(2, scheduler.sweep(today));
    }
}
