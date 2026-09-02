package za.co.handyflow.platform.facilities.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
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
import za.co.handyflow.platform.shared.TenantId;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Daily cross-tenant sweep, scheduled at 08:45 — the next free 15-minute
 * slot after TrainingProvider's own 08:30 sweep (established convention:
 * PSIRA 07:00, Armoury 07:15, DebtCollection 07:30, CollAgency 07:45,
 * Warehousing 08:00, Training-internal 08:15, TrainingProvider 08:30).
 * <p>
 * Four sweeps: (1) PPM schedules due today or earlier — generates a work
 * order per due schedule (skipping any schedule that already has an open
 * work order, so a slow-to-close job doesn't spawn duplicates every day
 * it stays open) and notifies tenant admins; (2) work orders overdue
 * against their own scheduled date; (3) compliance certificates expiring
 * within 30 days; (4) compliance certificates whose expiry date has
 * passed — transitions them to EXPIRED and notifies (a lapsed electrical
 * COC or fire equipment certificate is a real compliance/insurance risk,
 * hence CRITICAL severity — matching how TrainingProvider treats a
 * lapsed accreditation).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FacilityNotificationScheduler {

    private static final int CERT_EXPIRY_WARNING_DAYS = 30;

    private final FacilityPpmScheduleRepository ppmScheduleRepository;
    private final FacilityAssetRepository assetRepository;
    private final FacilityWorkOrderRepository workOrderRepository;
    private final FacilityComplianceCertificateRepository certificateRepository;
    private final FacilityWorkOrderService workOrderService;
    private final NotificationService notificationService;
    private final TenantAdminRecipients tenantAdminRecipients;

    @Scheduled(cron = "0 45 8 * * *")
    public void runDailySweep() {
        log.info("[Facilities] Daily sweep starting");
        int ppmGenerated = generateDuePpmWorkOrders();
        int overdueAlerts = alertOverdueWorkOrders();
        int expiringAlerts = alertExpiringCertificates();
        int expiredAlerts = expireAndAlertCertificates();
        log.info("[Facilities] Daily sweep complete — {} PPM work orders generated, {} overdue alerts, " +
                "{} expiring-certificate alerts, {} expired-certificate alerts",
                ppmGenerated, overdueAlerts, expiringAlerts, expiredAlerts);
    }

    @Transactional
    public int generateDuePpmWorkOrders() {
        List<FacilityPpmSchedule> due = ppmScheduleRepository.findAllDueAcrossTenants(LocalDate.now());
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
            } catch (Exception e) {
                log.error("[Facilities] Failed to generate PPM work order for schedule={}: {}",
                        schedule.getId(), e.getMessage(), e);
            }
        }
        return generated;
    }

    @Transactional(readOnly = true)
    public int alertOverdueWorkOrders() {
        List<FacilityWorkOrder> overdue = workOrderRepository.findOverdueAcrossTenants(LocalDate.now());
        int alerts = 0;
        for (FacilityWorkOrder wo : overdue) {
            try {
                notifyOverdueWorkOrder(wo.getTenantId(), wo);
                alerts++;
            } catch (Exception e) {
                log.error("[Facilities] Failed to send overdue alert for work order={}: {}", wo.getId(), e.getMessage(), e);
            }
        }
        return alerts;
    }

    @Transactional(readOnly = true)
    public int alertExpiringCertificates() {
        LocalDate cutoff = LocalDate.now().plusDays(CERT_EXPIRY_WARNING_DAYS);
        List<FacilityComplianceCertificate> candidates = certificateRepository.findAllValidWithExpiryAcrossTenants(cutoff);
        int alerts = 0;
        for (FacilityComplianceCertificate cert : candidates) {
            if (cert.isExpiringWithin(CERT_EXPIRY_WARNING_DAYS)) {
                try {
                    notifyCertificateExpiring(cert.getTenantId(), cert);
                    alerts++;
                } catch (Exception e) {
                    log.error("[Facilities] Failed to send expiring-certificate alert for certificate={}: {}",
                            cert.getId(), e.getMessage(), e);
                }
            }
        }
        return alerts;
    }

    @Transactional
    public int expireAndAlertCertificates() {
        List<FacilityComplianceCertificate> candidates = certificateRepository.findAllValidWithExpiryAcrossTenants(LocalDate.now());
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
