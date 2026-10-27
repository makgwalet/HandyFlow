package za.co.handyflow.platform.facilities.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import za.co.handyflow.platform.facilities.domain.model.FacilityVendor;
import za.co.handyflow.platform.facilities.domain.model.FacilityVendorDocument;
import za.co.handyflow.platform.facilities.domain.repository.FacilityVendorDocumentRepository;
import za.co.handyflow.platform.facilities.domain.repository.FacilityVendorRepository;
import za.co.handyflow.platform.notifications.domain.model.NotificationType;
import za.co.handyflow.platform.shared.SchedulerLease;

import java.time.Duration;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;

/**
 * Daily 08:50 sweep (after the facilities sweep at 08:45): reminds the tenant's administrators as a vendor's
 * insurance, tax or licence document nears expiry (30/14/7/3/1/0 days out) and once more the day after it
 * lapses, when the vendor stops being assignable. Reminders fire only on those milestones, never every day.
 * Runs under {@link SchedulerLease} so only one instance sweeps.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FacilityVendorDocumentScheduler {

    private static final String LOCK_NAME = "facilities-vendor-documents";
    private static final Duration LOCK_MAX_HOLD = Duration.ofMinutes(15);
    static final Set<Long> REMINDER_DAYS = Set.of(30L, 14L, 7L, 3L, 1L, 0L);
    static final long LAPSED_ALERT_DAY = -1L;

    private final FacilityVendorDocumentRepository documentRepository;
    private final FacilityVendorRepository vendorRepository;
    private final FacilityAlerts alerts;
    private final SchedulerLease schedulerLease;

    @Scheduled(cron = "0 50 8 * * *")
    public void runDailySweep() {
        schedulerLease.runExclusively(LOCK_NAME, LOCK_MAX_HOLD, () -> {
            int sent = sweep(FacilityTime.today());
            log.info("[Facilities] Vendor document sweep complete — {} alert(s)", sent);
        });
    }

    /** Package-visible for tests; returns the number of alerts raised. */
    int sweep(LocalDate today) {
        List<FacilityVendorDocument> candidates = documentRepository.findValidExpiringAcrossTenants(today.plusDays(30));
        int sent = 0;
        for (FacilityVendorDocument d : candidates) {
            long daysLeft = ChronoUnit.DAYS.between(today, d.getExpiryDate());
            boolean lapsedToday = daysLeft == LAPSED_ALERT_DAY;
            if (!lapsedToday && !REMINDER_DAYS.contains(daysLeft)) continue;
            try {
                String vendor = vendorRepository.findActiveById(d.getTenantId(), d.getVendorId())
                        .map(FacilityVendor::getCompanyName).orElse("a vendor");
                if (lapsedToday) {
                    alerts.toAdmins(d.getTenantId(), NotificationType.FACILITY_VENDOR_DOC_EXPIRED,
                            "Vendor document expired: " + vendor,
                            d.getDocType() + (d.getReference() != null ? " " + d.getReference() : "") + " expired on " + d.getExpiryDate()
                                    + ". " + vendor + " cannot be assigned work until it is renewed.",
                            "/facilities/vendors/" + d.getVendorId(), d.getId().toString());
                } else {
                    alerts.toAdmins(d.getTenantId(), NotificationType.FACILITY_VENDOR_DOC_EXPIRING,
                            "Vendor document expiring: " + vendor,
                            d.getDocType() + (d.getReference() != null ? " " + d.getReference() : "") + " expires in " + daysLeft
                                    + " day(s) on " + d.getExpiryDate() + ".",
                            "/facilities/vendors/" + d.getVendorId(), d.getId().toString());
                }
                sent++;
            } catch (Exception e) {
                log.error("[Facilities] Vendor document alert failed for document={}: {}", d.getId(), e.getMessage(), e);
            }
        }
        return sent;
    }
}
