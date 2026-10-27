package za.co.handyflow.platform.facilities.application.internal;

import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.facilities.domain.model.FacilityVendor;
import za.co.handyflow.platform.facilities.domain.model.FacilityVendorDocument;
import za.co.handyflow.platform.facilities.domain.repository.FacilityVendorDocumentRepository;
import za.co.handyflow.platform.facilities.domain.repository.FacilityVendorRepository;
import za.co.handyflow.platform.notifications.application.internal.NotificationService;
import za.co.handyflow.platform.notifications.domain.model.NotificationType;
import za.co.handyflow.platform.shared.TenantId;

import java.lang.reflect.Proxy;
import java.time.LocalDate;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class FacilityVendorDocumentSchedulerTest {

    private final TenantId tenant = TenantId.generate();
    private final LocalDate today = LocalDate.of(2026, 10, 27);
    private final FacilityVendor vendor = FacilityVendor.create(tenant, "ACME Plumbing", "PLUMBING", null, null, null, null);
    private final List<FacilityVendorDocument> docs = new ArrayList<>();
    private final List<NotificationType> sent = new ArrayList<>();
    private boolean alertsDown;

    private final FacilityVendorDocumentRepository docRepo = (FacilityVendorDocumentRepository) Proxy.newProxyInstance(
            getClass().getClassLoader(), new Class<?>[]{FacilityVendorDocumentRepository.class}, (p, m, a) -> {
                if ("findValidExpiringAcrossTenants".equals(m.getName())) return List.copyOf(docs);
                throw new UnsupportedOperationException(m.getName());
            });
    private final FacilityVendorRepository vendorRepo = (FacilityVendorRepository) Proxy.newProxyInstance(
            getClass().getClassLoader(), new Class<?>[]{FacilityVendorRepository.class}, (p, m, a) -> {
                if ("findActiveById".equals(m.getName())) return Optional.of(vendor);
                throw new UnsupportedOperationException(m.getName());
            });
    private final FacilityAlerts alerts = new FacilityAlerts(new NotificationService(null, null) {
        @Override public void send(za.co.handyflow.platform.notifications.application.NotificationRequest r) {
            if (alertsDown) throw new IllegalStateException("down");
            sent.add(r.type());
        }
    }, t -> List.of(new za.co.handyflow.platform.notifications.application.Recipient(UUID.randomUUID(), "Admin", "a@x.co.za", null)));
    private final FacilityVendorDocumentScheduler scheduler = new FacilityVendorDocumentScheduler(docRepo, vendorRepo, alerts, null);

    private void doc(int expiresInDays) {
        docs.add(FacilityVendorDocument.create(tenant, vendor.getId(), "COIDA_LETTER", "C1", null, null, today.plusDays(expiresInDays), null, null));
    }

    @Test
    void remindersFireOnMilestoneDaysOnly() {
        for (int d : new int[]{30, 14, 7, 3, 1, 0}) doc(d);
        for (int d : new int[]{29, 20, 10, 2}) doc(d);
        assertEquals(6, scheduler.sweep(today));
        assertTrue(sent.stream().allMatch(t -> t == NotificationType.FACILITY_VENDOR_DOC_EXPIRING));
    }

    @Test
    void aDocumentThatLapsedYesterdayRaisesTheCriticalAlertOnceAndThenStaysQuiet() {
        doc(-1);
        assertEquals(1, scheduler.sweep(today));
        assertEquals(List.of(NotificationType.FACILITY_VENDOR_DOC_EXPIRED), sent);
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
        assertEquals(2, scheduler.sweep(today)); // FacilityAlerts swallows delivery failures
    }
}
