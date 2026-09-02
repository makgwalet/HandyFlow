package za.co.handyflow.platform.agriculture.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.agriculture.domain.model.AgScoutingRecord;
import za.co.handyflow.platform.agriculture.domain.repository.AgScoutingRecordRepository;
import za.co.handyflow.platform.notifications.application.NotificationRequest;
import za.co.handyflow.platform.notifications.application.Recipient;
import za.co.handyflow.platform.notifications.application.TenantAdminRecipients;
import za.co.handyflow.platform.notifications.application.internal.NotificationService;
import za.co.handyflow.platform.notifications.domain.model.NotificationType;
import za.co.handyflow.platform.shared.TenantId;

import java.time.LocalDate;
import java.util.List;

/**
 * Daily sweep — 09:45, the next free 15-minute slot after this module's own
 * {@code AgNotificationScheduler} (Increment 1, 09:30), per this
 * engagement's running notification-scheduler registry. A SEPARATE
 * scheduler class from {@code AgNotificationScheduler} rather than merged
 * into it — Crops and Livestock are independent sub-domains within this
 * module (see package-info.java / this class's own entities' Javadoc for
 * why), and keeping each sub-domain's sweep in its own scheduler class
 * mirrors the "one scheduler per bounded concern" shape used elsewhere in
 * this engagement.
 * <p>
 * One sweep: scouting follow-ups due — every {@link AgScoutingRecord}
 * whose {@code followUpDate} has arrived and hasn't yet been acknowledged,
 * regardless of tenant. Same isolate-per-record pattern and same
 * "notify then acknowledge" shape as {@code AgNotificationScheduler}'s own
 * health-event sweep — see that class's Javadoc for the precedent.
 * <p>
 * Requires {@code NotificationType.AG_SCOUTING_FOLLOWUP_DUE} to exist —
 * see {@code Agriculture-Crops-NotificationType-patch-instructions.md} at
 * the sandbox root for the exact enum constant to add.
 * <p>
 * ASSUMES {@code @EnableScheduling} IS ALREADY ON somewhere in this app —
 * not re-verified here, same standing assumption {@code AgNotificationScheduler}
 * and every prior scheduler in this engagement has made.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AgCropNotificationScheduler {

    private final AgScoutingRecordRepository scoutingRecordRepository;
    private final NotificationService notificationService;
    private final TenantAdminRecipients tenantAdminRecipients;

    @Scheduled(cron = "0 45 9 * * *", zone = "Africa/Johannesburg")
    @Transactional
    public void runDailySweep() {
        sweepScoutingFollowUpsDue();
    }

    private void sweepScoutingFollowUpsDue() {
        LocalDate today = LocalDate.now();
        List<AgScoutingRecord> due = scoutingRecordRepository.findFollowUpDueAcrossTenants(today);
        int sent = 0;
        for (AgScoutingRecord record : due) {
            try {
                notifyScoutingFollowUpDue(record);
                record.acknowledgeFollowUp();
                scoutingRecordRepository.save(record);
                sent++;
            } catch (Exception e) {
                log.error("Failed to send scouting-follow-up-due reminder for record={}: {}",
                        record.getId(), e.getMessage(), e);
            }
        }
        if (sent > 0) {
            log.info("Sent {} agriculture scouting-follow-up-due reminder(s)", sent);
        }
    }

    private void notifyScoutingFollowUpDue(AgScoutingRecord record) {
        TenantId tenantId = record.getTenantId();
        List<Recipient> recipients = tenantAdminRecipients.resolveTenantAdmins(tenantId);
        if (recipients.isEmpty()) return;

        notificationService.send(NotificationRequest.builder()
                .tenantId(tenantId)
                .type(NotificationType.AG_SCOUTING_FOLLOWUP_DUE)
                .title("Scouting follow-up due: " + record.getObservationType())
                .message(record.getObservationType() + " follow-up is due (" + record.getFollowUpDate()
                        + ") for crop cycle " + record.getCropCycleId()
                        + (record.getDescription() != null ? " — " + record.getDescription() : "") + ".")
                .actionUrl("/agriculture/scouting-records/" + record.getId())
                .sourceModule("agriculture")
                .sourceEntityId(record.getId().toString())
                .recipients(recipients)
                .build());
    }
}
