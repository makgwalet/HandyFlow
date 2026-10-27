package za.co.handyflow.platform.facilities.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import za.co.handyflow.platform.facilities.domain.model.FacilityComplianceCertificate;
import za.co.handyflow.platform.facilities.domain.model.FacilityPpmSchedule;
import za.co.handyflow.platform.facilities.domain.model.FacilityWorkOrder;
import za.co.handyflow.platform.facilities.domain.repository.FacilityAssetRepository;
import za.co.handyflow.platform.facilities.domain.repository.FacilityComplianceCertificateRepository;
import za.co.handyflow.platform.facilities.domain.repository.FacilityPpmScheduleRepository;
import za.co.handyflow.platform.facilities.domain.repository.FacilityWorkOrderRepository;
import za.co.handyflow.platform.notifications.application.NotificationRequest;
import za.co.handyflow.platform.notifications.application.Recipient;
import za.co.handyflow.platform.notifications.application.TenantAdminRecipients;
import za.co.handyflow.platform.notifications.application.internal.NotificationService;
import za.co.handyflow.platform.notifications.domain.model.NotificationType;
import za.co.handyflow.platform.shared.SchedulerLease;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Duration;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Daily cross-tenant sweep, scheduled at 08:45 — the next free 15-minute
 * slot after TrainingProvider's own 08:30 sweep (established convention:
 * PSIRA 07:00, Armoury 07:15, DebtCollection 07:30, CollAgency 07:45,
 * Warehousing 08:00, Training-internal 08:15, TrainingProvider 08:30).
 * <p>
 * Four sweeps: (1) due PPM schedules generate a work order each (skipping a
 * schedule that already has an open one); (2) work orders overdue against
 * their scheduled date; (3) compliance certificates reaching a reminder
 * milestone; (4) certificates whose expiry date has passed are moved to
 * EXPIRED and alerted at CRITICAL severity.
 * <p>
 * Chunk 1 changes:
 * <ul>
 *   <li>Runs under {@link SchedulerLease}: only one app instance sweeps at a
 *       time, so no duplicate work orders or notifications when scaled out.</li>
 *   <li>Removed {@code @Transactional} from the sweep methods. They were
 *       invoked via {@code this.}, so Spring's proxy never applied it, and
 *       the intent is one transaction <em>per item</em> (each repository /
 *       service call has its own), so one bad schedule can't affect others.</li>
 *   <li>Expiry reminders fire only on milestone days (30/14/7/3/1/0 days
 *       out) instead of every day for 30 days.</li>
 *   <li>A concurrent duplicate PPM work order is rejected by the partial
 *       unique index (see migration) and handled as a benign skip.</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FacilityNotificationScheduler {

    private static final String LOCK_NAME = "facilities-daily-sweep";
    private static final Duration LOCK_MAX_HOLD = Duration.ofMinutes(30);
    private static final int CERT_EXPIRY_WARNING_DAYS = 30;
    private static final Set<Long> CERT_REMINDER_DAYS = Set.of(30L, 14L, 7L, 3L, 1L, 0L);

    private final FacilityPpmScheduleRepository ppmScheduleRepository;
    private final FacilityAssetRepository assetRepository;
    private final FacilityWorkOrderRepository workOrderRepository;
    private final FacilityComplianceCertificateRepository certificateRepository;
    private final FacilityWorkOrderService workOrderService;
    private final NotificationService notificationService;
    private final TenantAdminRecipients tenantAdminRecipients;
    private final SchedulerLease schedulerLease;

    @Scheduled(cron = "0 45 8 * * *")
    public void runDailySweep() {
        schedulerLease.runExclusively(LOCK_NAME, LOCK_MAX_HOLD, this::sweep);
    }

    private void sweep() {
        log.info("[Facilities] Daily sweep starting");
        int ppmGenerated = generateDuePpmWorkOrders();
        int overdueAlerts = alertOverdueWorkOrders();
        int expiringAlerts = alertExpiringCertificates();
        int expiredAlerts = expireAndAlertCertificates();
        log.info("[Facilities] Daily sweep complete — {} PPM work orders generated, {} overdue alerts, " +
                        "{} expiring-certificate alerts, {} expired-certificate alerts",
                ppmGenerated, overdueAlerts, expiringAlerts, expiredAlerts);
    }

    public int generateDuePpmWorkOrders() {
        List<FacilityPpmSchedule> due = ppmScheduleRepository.findAllDueAcrossTenants(FacilityTime.today());
        int generated = 0;
        for (FacilityPpmSchedule schedule : due) {
            try {
                TenantId tenantId = schedule.getTenantId();
                if (workOrderRepository.existsOpenForPpmSchedule(tenantId, schedule.getId())) {
                    continue; // already has an open work order — don't spawn a duplicate
                }
                Optional<UUID> siteId = assetRepository.findActiveById(tenantId, schedule.getAssetId())
                        .map(a -> a.getSiteId());
                if (siteId.isEmpty()) {
                    log.warn("[Facilities] PPM schedule={} references a missing/deleted asset — skipping", schedule.getId());
                    continue;
                }
                FacilityWorkOrder wo = workOrderService.createFromPpmSchedule(tenantId, schedule.getAssetId(),
                        siteId.get(), schedule.getId(), schedule.getTaskName(), schedule.getNextDueDate());
                notifyPpmDue(tenantId, schedule, wo);
                generated++;
            } catch (DataIntegrityViolationException e) {
                log.info("[Facilities] PPM schedule={} already has an open work order (concurrent creation) — skipped",
                        schedule.getId());
            } catch (Exception e) {
                log.error("[Facilities] Failed to generate PPM work order for schedule={}: {}",
                        schedule.getId(), e.getMessage(), e);
            }
        }
        return generated;
    }

    public int alertOverdueWorkOrders() {
        LocalDate today = FacilityTime.today();
        List<FacilityWorkOrder> overdue = workOrderRepository.findOverdueAcrossTenants(today);
        int alerts = 0;
        for (FacilityWorkOrder wo : overdue) {
            // Remind at milestones (1, 3, 7, 14, 30 days, then monthly) instead of every day forever.
            if (!FacilityOverduePolicy.shouldAlert(ChronoUnit.DAYS.between(wo.getScheduledDate(), today))) continue;
            try {
                notifyOverdueWorkOrder(wo.getTenantId(), wo);
                alerts++;
            } catch (Exception e) {
                log.error("[Facilities] Failed to send overdue alert for work order={}: {}", wo.getId(), e.getMessage(), e);
            }
        }
        return alerts;
    }

    public int alertExpiringCertificates() {
        LocalDate today = FacilityTime.today();
        LocalDate cutoff = today.plusDays(CERT_EXPIRY_WARNING_DAYS);
        List<FacilityComplianceCertificate> candidates = certificateRepository.findAllValidWithExpiryAcrossTenants(cutoff);
        int alerts = 0;
        for (FacilityComplianceCertificate cert : candidates) {
            long daysLeft = ChronoUnit.DAYS.between(today, cert.getExpiryDate());
            if (daysLeft < 0 || !CERT_REMINDER_DAYS.contains(daysLeft)) continue;
            try {
                notifyCertificateExpiring(cert.getTenantId(), cert);
                alerts++;
            } catch (Exception e) {
                log.error("[Facilities] Failed to send expiring-certificate alert for certificate={}: {}",
                        cert.getId(), e.getMessage(), e);
            }
        }
        return alerts;
    }

    public int expireAndAlertCertificates() {
        List<FacilityComplianceCertificate> candidates = certificateRepository.findAllValidWithExpiryAcrossTenants(FacilityTime.today());
        int alerts = 0;
        for (FacilityComplianceCertificate cert : candidates) {
            if (cert.isExpired()) {
                try {
                    cert.markExpired();
                    certificateRepository.save(cert);
                    notifyCertificateExpired(cert.getTenantId(), cert);
                    alerts++;
                } catch (Exception e) {
                    log.error("[Facilities] Failed to expire/alert certificate={}: {}", cert.getId(), e.getMessage(), e);
                }
            }
        }
        return alerts;
    }

    private void notifyPpmDue(TenantId tenantId, FacilityPpmSchedule schedule, FacilityWorkOrder wo) {
        List<Recipient> recipients = tenantAdminRecipients.resolveTenantAdmins(tenantId);
        if (recipients.isEmpty()) return;
        notificationService.send(NotificationRequest.builder()
                .tenantId(tenantId)
                .type(NotificationType.FACILITY_PPM_DUE)
                .title("PPM due: " + schedule.getTaskName())
                .message("Work order " + wo.getWorkOrderNumber() + " was generated for \"" + schedule.getTaskName()
                        + "\", due " + schedule.getNextDueDate() + ".")
                .actionUrl("/facilities/work-orders/" + wo.getId())
                .sourceModule("facilities")
                .sourceEntityId(wo.getId().toString())
                .recipients(recipients)
                .build());
    }

    private void notifyOverdueWorkOrder(TenantId tenantId, FacilityWorkOrder wo) {
        List<Recipient> recipients = tenantAdminRecipients.resolveTenantAdmins(tenantId);
        if (recipients.isEmpty()) return;
        notificationService.send(NotificationRequest.builder()
                .tenantId(tenantId)
                .type(NotificationType.FACILITY_WORKORDER_OVERDUE)
                .title("Work order overdue: " + wo.getWorkOrderNumber())
                .message(wo.getWorkOrderNumber() + " was scheduled for " + wo.getScheduledDate()
                        + " and is still " + wo.getStatus() + ".")
                .actionUrl("/facilities/work-orders/" + wo.getId())
                .sourceModule("facilities")
                .sourceEntityId(wo.getId().toString())
                .recipients(recipients)
                .build());
    }

    private void notifyCertificateExpiring(TenantId tenantId, FacilityComplianceCertificate cert) {
        List<Recipient> recipients = tenantAdminRecipients.resolveTenantAdmins(tenantId);
        if (recipients.isEmpty()) return;
        notificationService.send(NotificationRequest.builder()
                .tenantId(tenantId)
                .type(NotificationType.FACILITY_COMPLIANCE_EXPIRING)
                .title("Compliance certificate expiring: " + cert.getCertificateType())
                .message("Certificate " + (cert.getCertificateNumber() != null ? cert.getCertificateNumber() : cert.getId())
                        + " expires " + cert.getExpiryDate() + ".")
                .actionUrl("/facilities/compliance/" + cert.getId())
                .sourceModule("facilities")
                .sourceEntityId(cert.getId().toString())
                .recipients(recipients)
                .build());
    }

    private void notifyCertificateExpired(TenantId tenantId, FacilityComplianceCertificate cert) {
        List<Recipient> recipients = tenantAdminRecipients.resolveTenantAdmins(tenantId);
        if (recipients.isEmpty()) return;
        notificationService.send(NotificationRequest.builder()
                .tenantId(tenantId)
                .type(NotificationType.FACILITY_COMPLIANCE_EXPIRED)
                .title("Compliance certificate EXPIRED: " + cert.getCertificateType())
                .message("Certificate " + (cert.getCertificateNumber() != null ? cert.getCertificateNumber() : cert.getId())
                        + " expired " + cert.getExpiryDate() + " and requires renewal.")
                .actionUrl("/facilities/compliance/" + cert.getId())
                .sourceModule("facilities")
                .sourceEntityId(cert.getId().toString())
                .recipients(recipients)
                .build());
    }
}