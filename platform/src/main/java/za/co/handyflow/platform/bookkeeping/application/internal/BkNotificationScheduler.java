package za.co.handyflow.platform.bookkeeping.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.bookkeeping.domain.model.BkBankTransaction;
import za.co.handyflow.platform.bookkeeping.domain.model.BkInvoice;
import za.co.handyflow.platform.bookkeeping.domain.model.BkServiceAgreement;
import za.co.handyflow.platform.bookkeeping.domain.repository.BkBankTransactionRepository;
import za.co.handyflow.platform.bookkeeping.domain.repository.BkInvoiceRepository;
import za.co.handyflow.platform.bookkeeping.domain.repository.BkServiceAgreementRepository;
import za.co.handyflow.platform.notifications.application.NotificationRequest;
import za.co.handyflow.platform.notifications.application.Recipient;
import za.co.handyflow.platform.notifications.application.TenantAdminRecipients;
import za.co.handyflow.platform.notifications.application.internal.NotificationService;
import za.co.handyflow.platform.notifications.domain.model.NotificationType;
import za.co.handyflow.platform.shared.TenantId;

import java.time.LocalDate;
import java.util.List;

/**
 * Daily cross-tenant sweep, scheduled at 09:15 — the next free slot after
 * Module 5b's own Facilities Management sweep at 09:00 (established
 * convention: PSIRA 07:00, Armoury 07:15, DebtCollection 07:30, CollAgency
 * 07:45, Warehousing 08:00, Training-internal 08:15, TrainingProvider
 * 08:30, Facilities-internal 08:45, FacilitiesManagement 09:00).
 * <p>
 * Three sweeps: (1) bank transactions still unreconciled more than 14
 * days after their own transaction date — a real bookkeeping-practice
 * risk: the whole point of the practice is to keep a client's bank feed
 * current, and a stale reconciliation queue is exactly the kind of thing
 * a client complains about discovering at month-end; (2) service
 * agreements whose {@code endDate} falls within 30 days — a lapsed
 * retainer silently reverting to ad-hoc time-and-materials billing is a
 * real revenue-continuity risk; (3) invoices past their due date and not
 * yet PAID.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BkNotificationScheduler {

    private static final int UNRECONCILED_WARNING_DAYS = 14;
    private static final int AGREEMENT_EXPIRY_WARNING_DAYS = 30;

    private final BkBankTransactionRepository bankTransactionRepository;
    private final BkServiceAgreementRepository serviceAgreementRepository;
    private final BkInvoiceRepository invoiceRepository;
    private final NotificationService notificationService;
    private final TenantAdminRecipients tenantAdminRecipients;

    @Scheduled(cron = "0 15 9 * * *")
    public void runDailySweep() {
        log.info("[Bookkeeping] Daily sweep starting");
        int unreconciledAlerts = alertUnreconciledTransactions();
        int expiringAgreementAlerts = alertExpiringAgreements();
        int overdueInvoiceAlerts = alertOverdueInvoices();
        log.info("[Bookkeeping] Daily sweep complete — {} unreconciled-transaction alerts, " +
                "{} expiring-agreement alerts, {} overdue-invoice alerts",
                unreconciledAlerts, expiringAgreementAlerts, overdueInvoiceAlerts);
    }

    @Transactional(readOnly = true)
    public int alertUnreconciledTransactions() {
        LocalDate cutoff = LocalDate.now().minusDays(UNRECONCILED_WARNING_DAYS);
        List<BkBankTransaction> stale = bankTransactionRepository.findUnreconciledOlderThan(cutoff);
        int alerts = 0;
        for (BkBankTransaction transaction : stale) {
            try {
                notifyTransactionUnreconciled(transaction.getTenantId(), transaction);
                alerts++;
            } catch (Exception e) {
                log.error("[Bookkeeping] Failed to send unreconciled-transaction alert for transaction={}: {}",
                        transaction.getId(), e.getMessage(), e);
            }
        }
        return alerts;
    }

    @Transactional(readOnly = true)
    public int alertExpiringAgreements() {
        LocalDate today = LocalDate.now();
        LocalDate cutoff = today.plusDays(AGREEMENT_EXPIRY_WARNING_DAYS);
        List<BkServiceAgreement> expiring = serviceAgreementRepository.findExpiringAcrossTenants(today, cutoff);
        int alerts = 0;
        for (BkServiceAgreement agreement : expiring) {
            try {
                notifyAgreementExpiring(agreement.getTenantId(), agreement);
                alerts++;
            } catch (Exception e) {
                log.error("[Bookkeeping] Failed to send expiring-agreement alert for agreement={}: {}", agreement.getId(), e.getMessage(), e);
            }
        }
        return alerts;
    }

    @Transactional(readOnly = true)
    public int alertOverdueInvoices() {
        List<BkInvoice> overdue = invoiceRepository.findOverdueAcrossTenants();
        int alerts = 0;
        for (BkInvoice invoice : overdue) {
            try {
                notifyInvoiceOverdue(invoice.getTenantId(), invoice);
                alerts++;
            } catch (Exception e) {
                log.error("[Bookkeeping] Failed to send overdue-invoice alert for invoice={}: {}", invoice.getId(), e.getMessage(), e);
            }
        }
        return alerts;
    }

    private void notifyTransactionUnreconciled(TenantId tenantId, BkBankTransaction transaction) {
        List<Recipient> recipients = tenantAdminRecipients.resolveTenantAdmins(tenantId);
        if (recipients.isEmpty()) return;
        notificationService.send(NotificationRequest.builder()
                .tenantId(tenantId)
                .type(NotificationType.BK_TRANSACTION_UNRECONCILED)
                .title("Bank transaction unreconciled: " + transaction.getDescription())
                .message("A " + transaction.getTransactionType() + " of " + transaction.getAmount() + " dated "
                        + transaction.getTransactionDate() + " is still unreconciled.")
                .actionUrl("/bookkeeping/clients/" + transaction.getClientId() + "/bank-transactions/" + transaction.getId())
                .sourceModule("bookkeeping")
                .sourceEntityId(transaction.getId().toString())
                .recipients(recipients)
                .build());
    }

    private void notifyAgreementExpiring(TenantId tenantId, BkServiceAgreement agreement) {
        List<Recipient> recipients = tenantAdminRecipients.resolveTenantAdmins(tenantId);
        if (recipients.isEmpty()) return;
        notificationService.send(NotificationRequest.builder()
                .tenantId(tenantId)
                .type(NotificationType.BK_AGREEMENT_EXPIRING)
                .title("Service agreement expiring: " + agreement.getBillingType())
                .message("The " + agreement.getBillingType() + " agreement for client " + agreement.getClientId()
                        + " expires " + agreement.getEndDate() + ".")
                .actionUrl("/bookkeeping/service-agreements/" + agreement.getId())
                .sourceModule("bookkeeping")
                .sourceEntityId(agreement.getId().toString())
                .recipients(recipients)
                .build());
    }

    private void notifyInvoiceOverdue(TenantId tenantId, BkInvoice invoice) {
        List<Recipient> recipients = tenantAdminRecipients.resolveTenantAdmins(tenantId);
        if (recipients.isEmpty()) return;
        notificationService.send(NotificationRequest.builder()
                .tenantId(tenantId)
                .type(NotificationType.BK_INVOICE_OVERDUE)
                .title("Invoice overdue: " + invoice.getInvoiceNumber())
                .message(invoice.getInvoiceNumber() + " was due " + invoice.getDueDate()
                        + " with a balance of " + invoice.balance() + " still outstanding.")
                .actionUrl("/bookkeeping/invoices/" + invoice.getId())
                .sourceModule("bookkeeping")
                .sourceEntityId(invoice.getId().toString())
                .recipients(recipients)
                .build());
    }
}
