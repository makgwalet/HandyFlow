package za.co.handyflow.platform.agriculture.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import za.co.handyflow.platform.agriculture.domain.model.AgHealthEvent;
import za.co.handyflow.platform.agriculture.domain.model.AgInventoryItem;
import za.co.handyflow.platform.agriculture.domain.repository.AgHealthEventRepository;
import za.co.handyflow.platform.agriculture.domain.repository.AgInventoryItemRepository;
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
 * Daily sweep at 09:30 SAST: health events whose next-due date has arrived, and stock below its reorder level.
 * <ul>
 *   <li><b>Telling is not acknowledging.</b> The sweep records the day it sent a message ({@code reminderNotifiedOn},
 *       {@code lowStockNotifiedOn}) and never touches {@code reminderAcknowledged}, which only a person sets. The farm dashboard's
 *       attention list shows everything not yet acknowledged, so a due vaccination stays on it after the morning message.</li>
 *   <li><b>No daily nagging.</b> A message is sent the first time, then again every {@value AgReminderRules#REPEAT_DAYS} days
 *       while it is still open. A low-stock notice is forgotten once the item is back above its reorder level.</li>
 *   <li><b>Only for what is still there.</b> Sold, dead or deleted animals and groups, and inactive or deleted farms, are skipped.</li>
 *   <li><b>The right person.</b> The farm's manager if they are a platform user, otherwise the tenant admins. Messages name the tag or
 *       batch number and the farm, not a database id.</li>
 *   <li><b>One instance, one transaction per record.</b> The sweep holds a database lease so two instances do not both send, and each
 *       record is its own transaction, so one failure cannot undo the others' "notified" marks and make everything re-send.</li>
 * </ul>
 * Today is the South African date (see {@link AgDates}), not the server's.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AgNotificationScheduler {

    static final String LOCK = "agriculture-daily-sweep";

    private final AgHealthEventRepository healthEventRepository;
    private final AgInventoryItemRepository inventoryItemRepository;
    private final NotificationService notificationService;
    private final AgReminderSupport support;
    private final SchedulerLease lease;
    private final PlatformTransactionManager transactionManager;

    @Scheduled(cron = "0 30 9 * * *", zone = "Africa/Johannesburg")
    public void runDailySweep() {
        lease.runExclusively(LOCK, Duration.ofMinutes(30), () -> {
            LocalDate today = AgDates.today();
            sweepHealthEventsDue(today);
            sweepLowStockInventory(today);
        });
    }

    private void sweepHealthEventsDue(LocalDate today) {
        List<AgHealthEvent> due = healthEventRepository.findDueAcrossTenants(today, AgReminderRules.repeatBefore(today));
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        AtomicInteger sent = new AtomicInteger();
        for (AgHealthEvent event : due) {
            try {
                tx.executeWithoutResult(status -> {
                    if (!AgReminderRules.shouldNotifyDue(event.getNextDueDate(), event.isReminderAcknowledged(), event.getReminderNotifiedOn(), today)) return;
                    if (notifyHealthEventDue(event, today)) {
                        event.markReminderNotified(today);
                        healthEventRepository.save(event);
                        sent.incrementAndGet();
                    }
                });
            } catch (Exception e) {
                log.error("Failed to send health-event-due reminder for event={}: {}", event.getId(), e.getMessage(), e);
            }
        }
        if (sent.get() > 0) log.info("Sent {} agriculture health-event-due reminder(s)", sent.get());
    }

    private void sweepLowStockInventory(LocalDate today) {
        List<AgInventoryItem> lowStock = inventoryItemRepository.findBelowReorderLevelAcrossTenants(AgReminderRules.repeatBefore(today));
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        AtomicInteger sent = new AtomicInteger();
        for (AgInventoryItem item : lowStock) {
            try {
                tx.executeWithoutResult(status -> {
                    if (!AgReminderRules.isNoticeDue(item.getLowStockNotifiedOn(), today)) return;
                    if (notifyLowStock(item)) {
                        item.markLowStockNotified(today);
                        inventoryItemRepository.save(item);
                        sent.incrementAndGet();
                    }
                });
            } catch (Exception e) {
                log.error("Failed to send low-stock alert for item={}: {}", item.getId(), e.getMessage(), e);
            }
        }
        if (sent.get() > 0) log.info("Sent {} agriculture low-stock alert(s)", sent.get());
    }

    /** @return true if a message was sent; false when the target is gone or nobody could be told (it is tried again tomorrow). */
    private boolean notifyHealthEventDue(AgHealthEvent event, LocalDate today) {
        TenantId tenantId = event.getTenantId();
        Optional<AgReminderSupport.Target> target = support.forAnimalOrGroup(tenantId, event.getAnimalId(), event.getGroupId());
        if (target.isEmpty()) return false;
        List<Recipient> recipients = support.recipientsFor(tenantId, target.get().farmId());
        if (recipients.isEmpty()) return false;

        notificationService.send(NotificationRequest.builder()
                .tenantId(tenantId)
                .type(NotificationType.AG_HEALTH_EVENT_DUE)
                .title("Health event due: " + event.getEventType())
                .message(event.getEventType() + " for " + target.get().label() + " on " + target.get().farmName() + " is "
                        + AgReminderRules.dueWording(event.getNextDueDate(), today) + " (" + event.getNextDueDate() + ")"
                        + (event.getDescription() != null ? " — " + event.getDescription() : "") + ".")
                .actionUrl(AgActionUrls.animalOrGroup(target.get().farmId(), event.getAnimalId(), event.getGroupId()))
                .sourceModule("agriculture")
                .sourceEntityId(event.getId().toString())
                .recipients(recipients)
                .build());
        return true;
    }

    private boolean notifyLowStock(AgInventoryItem item) {
        TenantId tenantId = item.getTenantId();
        Optional<AgReminderSupport.Target> target = support.forFarm(tenantId, item.getFarmId(), item.getItemName());
        if (target.isEmpty()) return false;
        List<Recipient> recipients = support.recipientsFor(tenantId, target.get().farmId());
        if (recipients.isEmpty()) return false;

        notificationService.send(NotificationRequest.builder()
                .tenantId(tenantId)
                .type(NotificationType.AG_INVENTORY_LOW_STOCK)
                .title("Low stock: " + item.getItemName())
                .message(item.getItemName() + " on " + target.get().farmName() + " is at " + item.getCurrentQuantity() + " " + item.getUnitOfMeasure()
                        + " — below its reorder level of " + item.getReorderLevel() + " " + item.getUnitOfMeasure() + ".")
                .actionUrl(AgActionUrls.inventory(item.getFarmId()))
                .sourceModule("agriculture")
                .sourceEntityId(item.getId().toString())
                .recipients(recipients)
                .build());
        return true;
    }
}
