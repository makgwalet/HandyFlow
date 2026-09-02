package za.co.handyflow.platform.facilitiesmanagement.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.facilitiesmanagement.domain.model.*;
import za.co.handyflow.platform.facilitiesmanagement.domain.repository.*;
import za.co.handyflow.platform.notifications.application.NotificationRequest;
import za.co.handyflow.platform.notifications.application.Recipient;
import za.co.handyflow.platform.notifications.application.TenantAdminRecipients;
import za.co.handyflow.platform.notifications.application.internal.NotificationService;
import za.co.handyflow.platform.notifications.domain.model.NotificationType;
import za.co.handyflow.platform.shared.TenantId;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Daily cross-tenant sweep, scheduled at 09:00 — the next free slot after
 * Module 5a's own Facilities sweep at 08:45 (established convention: PSIRA
 * 07:00, Armoury 07:15, DebtCollection 07:30, CollAgency 07:45, Warehousing
 * 08:00, Training-internal 08:15, TrainingProvider 08:30, Facilities 08:45).
 * <p>
 * Four sweeps: (1) PPM schedules due today or earlier — generates a work
 * order per due schedule (skipping any schedule that already has an open
 * work order, so a slow-to-close job doesn't spawn duplicates every day it
 * stays open), mirroring {@code FacilityNotificationScheduler}'s own PPM
 * sweep exactly, resolving the owning site/client via the schedule's asset;
 * (2) work orders overdue against their own scheduled date; (3) service
 * agreements whose {@code endDate} falls within 30 days — a lapsed
 * retainer silently reverting to ad-hoc time-and-materials billing is a
 * real revenue-continuity risk for an FM company; (4) invoices past their
 * due date and not yet PAID.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FmNotificationScheduler {

    private static final int AGREEMENT_EXPIRY_WARNING_DAYS = 30;

    private final FmPpmScheduleRepository ppmScheduleRepository;
    private final FmAssetRepository assetRepository;
    private final FmSiteRepository siteRepository;
    private final FmWorkOrderRepository workOrderRepository;
    private final FmServiceAgreementRepository serviceAgreementRepository;
    private final FmInvoiceRepository invoiceRepository;
    private final FmWorkOrderService workOrderService;
    private final NotificationService notificationService;
    private final TenantAdminRecipients tenantAdminRecipients;

    @Scheduled(cron = "0 0 9 * * *")
    public void runDailySweep() {
        log.info("[FM] Daily sweep starting");
        int ppmGenerated = generateDuePpmWorkOrders();
        int overdueWoAlerts = alertOverdueWorkOrders();
        int expiringAgreementAlerts = alertExpiringAgreements();
        int overdueInvoiceAlerts = alertOverdueInvoices();
        log.info("[FM] Daily sweep complete — {} PPM work orders generated, {} overdue-work-order alerts, " +
                "{} expiring-agreement alerts, {} overdue-invoice alerts",
                ppmGenerated, overdueWoAlerts, expiringAgreementAlerts, overdueInvoiceAlerts);
    }

    @Transactional
    public int generateDuePpmWorkOrders() {
        List<FmPpmSchedule> due = ppmScheduleRepository.findAllDueAcrossTenants(LocalDate.now());
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
            } catch (Exception e) {
                log.error("[FM] Failed to generate PPM work order for schedule={}: {}", schedule.getId(), e.getMessage(), e);
            }
        }
        return generated;
    }

    @Transactional(readOnly = true)
    public int alertOverdueWorkOrders() {
        List<FmWorkOrder> overdue = workOrderRepository.findOverdueAcrossTenants(LocalDate.now());
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

    @Transactional(readOnly = true)
    public int alertExpiringAgreements() {
        LocalDate today = LocalDate.now();
        LocalDate cutoff = today.plusDays(AGREEMENT_EXPIRY_WARNING_DAYS);
        List<FmServiceAgreement> expiring = serviceAgreementRepository.findExpiringAcrossTenants(today, cutoff);
        int alerts = 0;
        for (FmServiceAgreement agreement : expiring) {
            try {
                notifyAgreementExpiring(agreement.getTenantId(), agreement);
                alerts++;
            } catch (Exception e) {
                log.error("[FM] Failed to send expiring-agreement alert for agreement={}: {}", agreement.getId(), e.getMessage(), e);
            }
        }
        return alerts;
    }

    @Transactional(readOnly = true)
    public int alertOverdueInvoices() {
        List<FmInvoice> overdue = invoiceRepository.findOverdueAcrossTenants();
        int alerts = 0;
        for (FmInvoice invoice : overdue) {
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
