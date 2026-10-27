package za.co.handyflow.platform.facilitiesmanagement.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import za.co.handyflow.platform.facilitiesmanagement.domain.model.*;
import za.co.handyflow.platform.facilitiesmanagement.domain.repository.*;
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

/**
 * Daily cross-tenant sweep, scheduled at 09:00 — the next free slot after
 * Module 5a's Facilities sweep at 08:45.
 * <p>
 * Four sweeps: (1) due PPM schedules generate a work order each, resolving
 * the owning site/client via the schedule's asset (skipping a schedule that
 * already has an open one); (2) work orders overdue against their scheduled
 * date; (3) service agreements reaching an expiry reminder milestone;
 * (4) invoices past due and not yet PAID, at overdue milestones.
 * <p>
 * Chunk 2 changes:
 * <ul>
 *   <li>Runs under {@link SchedulerLease} — one instance sweeps at a time.</li>
 *   <li>Removed {@code @Transactional} from the sweep methods: they were
 *       invoked via {@code this.} so the proxy never applied it, and the
 *       intent is one transaction per item.</li>
 *   <li>Agreement-expiry reminders fire at 30/14/7/1 days out; overdue-invoice
 *       reminders fire at 1/7/14/30/60/90 days overdue — not every day.</li>
 *   <li>A concurrent duplicate PPM work order is rejected by the partial
 *       unique index (see migration) and treated as a benign skip.</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FmNotificationScheduler {

    private static final String LOCK_NAME = "fm-daily-sweep";
    private static final Duration LOCK_MAX_HOLD = Duration.ofMinutes(30);
    private static final int AGREEMENT_EXPIRY_WARNING_DAYS = 30;
    private static final Set<Long> AGREEMENT_REMINDER_DAYS = Set.of(30L, 14L, 7L, 1L);
    private static final Set<Long> INVOICE_OVERDUE_REMINDER_DAYS = Set.of(1L, 7L, 14L, 30L, 60L, 90L);

    private final FmPpmScheduleRepository ppmScheduleRepository;
    private final FmAssetRepository assetRepository;
    private final FmSiteRepository siteRepository;
    private final FmWorkOrderRepository workOrderRepository;
    private final FmServiceAgreementRepository serviceAgreementRepository;
    private final FmInvoiceRepository invoiceRepository;
    private final FmWorkOrderService workOrderService;
    private final NotificationService notificationService;
    private final TenantAdminRecipients tenantAdminRecipients;
    private final SchedulerLease schedulerLease;

    @Scheduled(cron = "0 0 9 * * *")
    public void runDailySweep() {
        schedulerLease.runExclusively(LOCK_NAME, LOCK_MAX_HOLD, this::sweep);
    }

    private void sweep() {
        log.info("[FM] Daily sweep starting");
        int ppmGenerated = generateDuePpmWorkOrders();
        int overdueWoAlerts = alertOverdueWorkOrders();
        int expiringAgreementAlerts = alertExpiringAgreements();
        int overdueInvoiceAlerts = alertOverdueInvoices();
        log.info("[FM] Daily sweep complete — {} PPM work orders generated, {} overdue-work-order alerts, " +
                        "{} expiring-agreement alerts, {} overdue-invoice alerts",
                ppmGenerated, overdueWoAlerts, expiringAgreementAlerts, overdueInvoiceAlerts);
    }

    public int generateDuePpmWorkOrders() {
        List<FmPpmSchedule> due = ppmScheduleRepository.findAllDueAcrossTenants(FmTime.today());
        int generated = 0;
        for (FmPpmSchedule schedule : due) {
            try {
                TenantId tenantId = schedule.getTenantId();
                if (workOrderRepository.existsOpenForPpmSchedule(tenantId, schedule.getId())) {
                    continue; // already has an open work order — don't spawn a duplicate
                }
                Optional<FmAsset> asset = assetRepository.findActiveById(tenantId, schedule.getAssetId());
                if (asset.isEmpty()) {
                    log.warn("[FM] PPM schedule={} references a missing/deleted asset — skipping", schedule.getId());
                    continue;
                }
                Optional<FmSite> site = siteRepository.findActiveById(tenantId, asset.get().getSiteId());
                if (site.isEmpty()) {
                    log.warn("[FM] PPM schedule={} references an asset whose site is missing/deleted — skipping", schedule.getId());
                    continue;
                }
                FmWorkOrder wo = workOrderService.createFromPpmSchedule(tenantId, site.get().getClientId(),
                        schedule.getAssetId(), site.get().getId(), schedule.getId(), schedule.getTaskName(), schedule.getNextDueDate());
                notifyPpmDue(tenantId, schedule, wo);
                generated++;
            } catch (DataIntegrityViolationException e) {
                log.info("[FM] PPM schedule={} already has an open work order (concurrent creation) — skipped", schedule.getId());
            } catch (Exception e) {
                log.error("[FM] Failed to generate PPM work order for schedule={}: {}", schedule.getId(), e.getMessage(), e);
            }
        }
        return generated;
    }

    public int alertOverdueWorkOrders() {
        List<FmWorkOrder> overdue = workOrderRepository.findOverdueAcrossTenants(FmTime.today());
        int alerts = 0;
        for (FmWorkOrder wo : overdue) {
            try {
                notifyOverdueWorkOrder(wo.getTenantId(), wo);
                alerts++;
            } catch (Exception e) {
                log.error("[FM] Failed to send overdue alert for work order={}: {}", wo.getId(), e.getMessage(), e);
            }
        }
        return alerts;
    }

    public int alertExpiringAgreements() {
        LocalDate today = FmTime.today();
        LocalDate cutoff = today.plusDays(AGREEMENT_EXPIRY_WARNING_DAYS);
        List<FmServiceAgreement> expiring = serviceAgreementRepository.findExpiringAcrossTenants(today, cutoff);
        int alerts = 0;
        for (FmServiceAgreement agreement : expiring) {
            long daysLeft = ChronoUnit.DAYS.between(today, agreement.getEndDate());
            if (!AGREEMENT_REMINDER_DAYS.contains(daysLeft)) continue;
            try {
                notifyAgreementExpiring(agreement.getTenantId(), agreement);
                alerts++;
            } catch (Exception e) {
                log.error("[FM] Failed to send expiring-agreement alert for agreement={}: {}", agreement.getId(), e.getMessage(), e);
            }
        }
        return alerts;
    }

    public int alertOverdueInvoices() {
        LocalDate today = FmTime.today();
        List<FmInvoice> overdue = invoiceRepository.findOverdueAcrossTenants();
        int alerts = 0;
        for (FmInvoice invoice : overdue) {
            long daysOverdue = ChronoUnit.DAYS.between(invoice.getDueDate(), today);
            if (!INVOICE_OVERDUE_REMINDER_DAYS.contains(daysOverdue)) continue;
            try {
                notifyInvoiceOverdue(invoice.getTenantId(), invoice);
                alerts++;
            } catch (Exception e) {
                log.error("[FM] Failed to send overdue-invoice alert for invoice={}: {}", invoice.getId(), e.getMessage(), e);
            }
        }
        return alerts;
    }

    private void notifyPpmDue(TenantId tenantId, FmPpmSchedule schedule, FmWorkOrder wo) {
        List<Recipient> recipients = tenantAdminRecipients.resolveTenantAdmins(tenantId);
        if (recipients.isEmpty()) return;
        notificationService.send(NotificationRequest.builder()
                .tenantId(tenantId)
                .type(NotificationType.FM_PPM_DUE)
                .title("PPM due: " + schedule.getTaskName())
                .message("Work order " + wo.getWorkOrderNumber() + " was generated for \"" + schedule.getTaskName()
                        + "\", due " + schedule.getNextDueDate() + ".")
                .actionUrl("/facilitiesmanagement/work-orders/" + wo.getId())
                .sourceModule("facilitiesmanagement")
                .sourceEntityId(wo.getId().toString())
                .recipients(recipients)
                .build());
    }

    private void notifyOverdueWorkOrder(TenantId tenantId, FmWorkOrder wo) {
        List<Recipient> recipients = tenantAdminRecipients.resolveTenantAdmins(tenantId);
        if (recipients.isEmpty()) return;
        notificationService.send(NotificationRequest.builder()
                .tenantId(tenantId)
                .type(NotificationType.FM_WORKORDER_OVERDUE)
                .title("Work order overdue: " + wo.getWorkOrderNumber())
                .message(wo.getWorkOrderNumber() + " was scheduled for " + wo.getScheduledDate()
                        + " and is still " + wo.getStatus() + ".")
                .actionUrl("/facilitiesmanagement/work-orders/" + wo.getId())
                .sourceModule("facilitiesmanagement")
                .sourceEntityId(wo.getId().toString())
                .recipients(recipients)
                .build());
    }

    private void notifyAgreementExpiring(TenantId tenantId, FmServiceAgreement agreement) {
        List<Recipient> recipients = tenantAdminRecipients.resolveTenantAdmins(tenantId);
        if (recipients.isEmpty()) return;
        notificationService.send(NotificationRequest.builder()
                .tenantId(tenantId)
                .type(NotificationType.FM_AGREEMENT_EXPIRING)
                .title("Service agreement expiring: " + agreement.getBillingType())
                .message("The " + agreement.getBillingType() + " agreement for client " + agreement.getClientId()
                        + " expires " + agreement.getEndDate() + ".")
                .actionUrl("/facilitiesmanagement/service-agreements/" + agreement.getId())
                .sourceModule("facilitiesmanagement")
                .sourceEntityId(agreement.getId().toString())
                .recipients(recipients)
                .build());
    }

    private void notifyInvoiceOverdue(TenantId tenantId, FmInvoice invoice) {
        List<Recipient> recipients = tenantAdminRecipients.resolveTenantAdmins(tenantId);
        if (recipients.isEmpty()) return;
        notificationService.send(NotificationRequest.builder()
                .tenantId(tenantId)
                .type(NotificationType.FM_INVOICE_OVERDUE)
                .title("Invoice overdue: " + invoice.getInvoiceNumber())
                .message(invoice.getInvoiceNumber() + " was due " + invoice.getDueDate()
                        + " with a balance of " + invoice.balance() + " still outstanding.")
                .actionUrl("/facilitiesmanagement/invoices/" + invoice.getId())
                .sourceModule("facilitiesmanagement")
                .sourceEntityId(invoice.getId().toString())
                .recipients(recipients)
                .build());
    }
}