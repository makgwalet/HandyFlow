package za.co.handyflow.platform.legalpractice.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.legalpractice.domain.model.LpMatterKeyDate;
import za.co.handyflow.platform.legalpractice.domain.repository.LpMatterKeyDateRepository;
import za.co.handyflow.platform.notifications.application.NotificationRequest;
import za.co.handyflow.platform.notifications.application.Recipient;
import za.co.handyflow.platform.notifications.application.TenantAdminRecipients;
import za.co.handyflow.platform.notifications.application.internal.NotificationService;
import za.co.handyflow.platform.notifications.domain.model.NotificationType;
import za.co.handyflow.platform.shared.TenantId;

import java.time.LocalDate;
import java.util.List;

/**
 * Daily sweep of {@link LpMatterKeyDate} rows that are due/overdue and
 * still unacknowledged — court dates, prescription deadlines, filing
 * deadlines. Runs at **10:00 SAST**, the next free slot in this
 * engagement's running notification-scheduler registry after
 * Agriculture-Crops' own 09:45.
 * <p>
 * Mirrors {@code AgNotificationScheduler}'s/{@code CollAgencyNotificationScheduler}'s
 * exact shape: a non-tenant-prefixed cross-tenant repository query
 * ({@link LpMatterKeyDateRepository#findDueUnacknowledgedAcrossTenants}),
 * {@link TenantAdminRecipients#resolveTenantAdmins} to resolve who gets
 * notified (no per-matter "responsible person" contact exists on
 * {@code LpMatterKeyDate} itself), and a per-row try/catch so one bad row
 * never stops the rest of the batch — same isolation principle
 * {@code ApBillDueSoonScheduler} already uses.
 * <p>
 * Fires the notification once per due date, then calls
 * {@link LpMatterKeyDate#acknowledge()} — the same "notify once, not
 * indefinitely" shape {@code AgHealthEvent}'s own sweep already
 * established. A human still has to separately call
 * {@code complete()}/{@code markMissed()} once the actual court date/
 * deadline has been dealt with; acknowledging only silences the reminder.
 * <p>
 * Requires a new {@code NotificationType.LP_MATTER_KEYDATE_DUE} constant —
 * this synced-source-only session cannot edit the real enum file, so the
 * exact addition is delivered separately as
 * {@code LegalPractice-NotificationType-patch-instructions.md} at the
 * sandbox root. This class will not compile until that patch is applied.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LpNotificationScheduler {

    private final LpMatterKeyDateRepository keyDateRepo;
    private final NotificationService notificationService;
    private final TenantAdminRecipients tenantAdminRecipients;

    @Scheduled(cron = "0 0 10 * * *", zone = "Africa/Johannesburg")
    @Transactional
    public void sweepDueKeyDates() {
        List<LpMatterKeyDate> due = keyDateRepo.findDueUnacknowledgedAcrossTenants(LocalDate.now());
        int sent = 0;

        for (LpMatterKeyDate keyDate : due) {
            try {
                notifyKeyDateDue(keyDate);
                keyDate.acknowledge();
                keyDateRepo.save(keyDate);
                sent++;
            } catch (Exception e) {
                log.error("Failed to raise key-date reminder for matterKeyDate={}: {}",
                        keyDate.getId(), e.getMessage(), e);
            }
        }

        if (sent > 0) {
            log.info("[LegalPractice] Sent {} matter key-date reminder(s)", sent);
        }
    }

    private void notifyKeyDateDue(LpMatterKeyDate keyDate) {
        TenantId tenantId = keyDate.getTenantId();
        List<Recipient> recipients = tenantAdminRecipients.resolveTenantAdmins(tenantId);
        if (recipients.isEmpty()) {
            return;
        }

        boolean overdue = keyDate.getDueDate().isBefore(LocalDate.now());
        notificationService.send(NotificationRequest.builder()
                .tenantId(tenantId)
                .type(NotificationType.LP_MATTER_KEYDATE_DUE)
                .title((overdue ? "Overdue: " : "Due today: ") + keyDate.getDateType())
                .message(keyDate.getDescription() + " — due " + keyDate.getDueDate()
                        + " (matter " + keyDate.getMatterId() + ")")
                .actionUrl("/legal-practice/matters/" + keyDate.getMatterId() + "/key-dates")
                .sourceModule("legalpractice")
                .sourceEntityId(keyDate.getId().toString())
                .recipients(recipients)
                .build());
    }
}
