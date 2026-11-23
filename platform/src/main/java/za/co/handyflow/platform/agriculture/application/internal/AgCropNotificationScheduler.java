package za.co.handyflow.platform.agriculture.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import za.co.handyflow.platform.agriculture.domain.model.AgScoutingRecord;
import za.co.handyflow.platform.agriculture.domain.repository.AgScoutingRecordRepository;
import za.co.handyflow.platform.agriculture.domain.rules.AgActionUrls;
import za.co.handyflow.platform.agriculture.domain.rules.AgDates;
import za.co.handyflow.platform.agriculture.domain.rules.AgReminderRules;
import za.co.handyflow.platform.notifications.application.NotificationRequest;
import za.co.handyflow.platform.notifications.application.Recipient;
import za.co.handyflow.platform.notifications.application.internal.NotificationService;
import za.co.handyflow.platform.notifications.domain.model.NotificationType;
import za.co.handyflow.platform.shared.SchedulerLease;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Daily sweep at 09:45 SAST: scouting follow-ups whose date has arrived. Same rules as {@link AgNotificationScheduler}: telling is not
 * acknowledging ({@code followUpNotifiedOn} is the sweep's, {@code followUpAcknowledged} is a person's), repeated weekly while open, only for
 * crop cycles still running on a live farm, sent to the farm manager (else the tenant admins), one lease and one transaction per record.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AgCropNotificationScheduler {

    static final String LOCK = "agriculture-crop-sweep";

    private final AgScoutingRecordRepository scoutingRecordRepository;
    private final NotificationService notificationService;
    private final AgReminderSupport support;
    private final SchedulerLease lease;
    private final PlatformTransactionManager transactionManager;

    @Scheduled(cron = "0 45 9 * * *", zone = "Africa/Johannesburg")
    public void runDailySweep() {
        lease.runExclusively(LOCK, Duration.ofMinutes(30), () -> sweepScoutingFollowUpsDue(AgDates.today()));
    }

    private void sweepScoutingFollowUpsDue(LocalDate today) {
        List<AgScoutingRecord> due = scoutingRecordRepository.findFollowUpDueAcrossTenants(today, AgReminderRules.repeatBefore(today));
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        AtomicInteger sent = new AtomicInteger();
        for (AgScoutingRecord record : due) {
            try {
                tx.executeWithoutResult(status -> {
                    if (!AgReminderRules.shouldNotifyDue(record.getFollowUpDate(), record.isFollowUpAcknowledged(), record.getFollowUpNotifiedOn(), today)) return;
                    if (notifyScoutingFollowUpDue(record, today)) {
                        record.markFollowUpNotified(today);
                        scoutingRecordRepository.save(record);
                        sent.incrementAndGet();
                    }
                });
            } catch (Exception e) {
                log.error("Failed to send scouting-follow-up-due reminder for record={}: {}", record.getId(), e.getMessage(), e);
            }
        }
        if (sent.get() > 0) log.info("Sent {} agriculture scouting-follow-up-due reminder(s)", sent.get());
    }

    /** @return true if a message was sent; false when the crop cycle or farm is gone or nobody could be told (tried again tomorrow). */
    private boolean notifyScoutingFollowUpDue(AgScoutingRecord record, LocalDate today) {
        TenantId tenantId = record.getTenantId();
        Optional<AgReminderSupport.Target> target = support.forCropCycle(tenantId, record.getCropCycleId());
        if (target.isEmpty()) return false;
        List<Recipient> recipients = support.recipientsFor(tenantId, target.get().farmId());
        if (recipients.isEmpty()) return false;

        notificationService.send(NotificationRequest.builder()
                .tenantId(tenantId)
                .type(NotificationType.AG_SCOUTING_FOLLOWUP_DUE)
                .title("Scouting follow-up due: " + record.getObservationType())
                .message(record.getObservationType() + " follow-up for " + target.get().label() + " on " + target.get().farmName() + " is "
                        + AgReminderRules.dueWording(record.getFollowUpDate(), today) + " (" + record.getFollowUpDate() + ")"
                        + (record.getDescription() != null ? " — " + record.getDescription() : "") + ".")
                .actionUrl(AgActionUrls.cropCycles(target.get().farmId()))
                .sourceModule("agriculture")
                .sourceEntityId(record.getId().toString())
                .recipients(recipients)
                .build());
        return true;
    }
}
