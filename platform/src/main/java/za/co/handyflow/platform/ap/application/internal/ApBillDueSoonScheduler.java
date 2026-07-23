package za.co.handyflow.platform.ap.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.ap.domain.model.ApBill;
import za.co.handyflow.platform.ap.domain.repository.ApBillRepository;
import za.co.handyflow.platform.notifications.application.NotificationRequest;
import za.co.handyflow.platform.notifications.application.Recipient;
import za.co.handyflow.platform.notifications.application.TenantAdminRecipients;
import za.co.handyflow.platform.notifications.application.internal.NotificationService;
import za.co.handyflow.platform.notifications.domain.model.NotificationType;
import za.co.handyflow.platform.shared.TenantId;

import java.time.LocalDate;
import java.util.List;

/**
 * Sends a one-time reminder for bills approaching their due date within a
 * configurable window (default 3 days). Separate scheduled class rather
 * than a method on ApService, matching the same convention already
 * established for scheduled jobs elsewhere in this codebase
 * (AccountantScheduler, PsiraComplianceScheduler, NoShowAlertScheduler,
 * and the recruiter module's InterviewReminderScheduler).
 * <p>
 * WHY DAILY, NOT HOURLY LIKE InterviewReminderScheduler? Interview times
 * are precise (a specific hour matters); bill due dates are whole days —
 * there's no meaningful difference between catching a bill at 7am vs 9am
 * on the same day, so a single daily run is enough and cheaper than
 * hourly polling.
 * <p>
 * Recipients: same tradeoff already made for BILL_PENDING_APPROVAL in
 * ApService — TenantAdminRecipients, not a narrower "everyone with
 * AP_READ/AP_MANAGE" resolution, since no such port exists.
 * <p>
 * ASSUMES @EnableScheduling IS ALREADY ON somewhere in this app — not
 * re-verified here, but already confirmed true elsewhere this session
 * (the recruiter module's InterviewReminderScheduler relies on the same
 * assumption and nothing has contradicted it).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ApBillDueSoonScheduler {

    private final ApBillRepository      billRepo;
    private final NotificationService   notificationService;
    private final TenantAdminRecipients tenantAdminRecipients;

    @Value("${ap.bill-due-soon.days-before:3}")
    private int daysBefore;

    @Scheduled(cron = "0 0 7 * * *") // once daily, 07:00
    @Transactional
    public void sendDueSoonReminders() {
        LocalDate today = LocalDate.now();
        LocalDate windowEnd = today.plusDays(daysBefore);
        List<ApBill> due = billRepo.findDueSoonAcrossTenants(today, windowEnd);

        for (ApBill bill : due) {
            try {
                notifyDueSoon(bill);
                bill.markDueSoonReminderSent();
                billRepo.save(bill);
            } catch (Exception e) {
                // One bad bill (bad tenant data, a notification failure,
                // whatever) must never stop the rest of the batch — same
                // isolation principle used throughout the notification
                // module's own channel senders.
                log.error("Failed to send due-soon reminder for bill={}: {}",
                        bill.getId(), e.getMessage(), e);
            }
        }

        if (!due.isEmpty()) {
            log.info("Sent {} bill-due-soon reminder(s)", due.size());
        }
    }

    private void notifyDueSoon(ApBill bill) {
        TenantId tenantId = bill.getTenantId();
        List<Recipient> recipients = tenantAdminRecipients.resolveTenantAdmins(tenantId);
        if (recipients.isEmpty()) return;

        notificationService.send(NotificationRequest.builder()
                .tenantId(tenantId)
                .type(NotificationType.BILL_DUE_SOON)
                .title("Bill due soon: " + bill.getSupplierName())
                .message(bill.getSupplierName() + " — " + bill.getBillNumber()
                        + " (R " + bill.getTotalAmount() + ") is due on " + bill.getDueDate() + ".")
                .actionUrl("/ap/bills/" + bill.getId())
                .sourceModule("ap")
                .sourceEntityId(bill.getId().toString())
                .recipients(recipients)
                .build());
    }
}