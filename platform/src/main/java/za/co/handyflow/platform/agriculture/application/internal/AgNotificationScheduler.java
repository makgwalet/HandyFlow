package za.co.handyflow.platform.agriculture.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.agriculture.domain.model.AgHealthEvent;
import za.co.handyflow.platform.agriculture.domain.model.AgInventoryItem;
import za.co.handyflow.platform.agriculture.domain.repository.AgHealthEventRepository;
import za.co.handyflow.platform.agriculture.domain.repository.AgInventoryItemRepository;
import za.co.handyflow.platform.notifications.application.NotificationRequest;
import za.co.handyflow.platform.notifications.application.Recipient;
import za.co.handyflow.platform.notifications.application.TenantAdminRecipients;
import za.co.handyflow.platform.notifications.application.internal.NotificationService;
import za.co.handyflow.platform.notifications.domain.model.NotificationType;
import za.co.handyflow.platform.shared.TenantId;

import java.time.LocalDate;
import java.util.List;

/**
 * Daily sweep — 09:30, the next free 15-minute slot after Bookkeeping's own
 * 09:15 (per this engagement's running notification-scheduler registry).
 * <p>
 * Two sweeps, each isolated per-record so one bad row never stops the rest
 * of the batch — same isolation principle {@code ApBillDueSoonScheduler}
 * uses:
 * <ol>
 *   <li>Health events whose {@code nextDueDate} has arrived and are not yet
 *       acknowledged — notify tenant admins, then call
 *       {@code AgHealthEvent.acknowledgeReminder()} so the alert fires once
 *       per due date, not indefinitely (see {@code AgHealthEvent}'s own
 *       Javadoc for why this is a deliberate Increment 1 simplification).</li>
 *   <li>Inventory items that have dropped below their reorder level —
 *       notify tenant admins. No acknowledgement flag: crossing back above
 *       the level self-corrects the condition, so a once-per-day digest is
 *       acceptable without extra state to track.</li>
 * </ol>
 * <p>
 * Requires {@code NotificationType.AG_HEALTH_EVENT_DUE} and
 * {@code NotificationType.AG_INVENTORY_LOW_STOCK} to exist — see
 * {@code Agriculture-NotificationType-patch-instructions.md} at the sandbox
 * root for the exact enum constants to add.
 * <p>
 * {@code NotificationService}/{@code TenantAdminRecipients} confirmed as
 * injected beans (not static calls), matching {@code FmWorkOrderService}/
 * {@code FacilityNotificationScheduler}'s real source.
 * <p>
 * ASSUMES {@code @EnableScheduling} IS ALREADY ON somewhere in this app —
 * not re-verified here, same standing assumption every prior scheduler in
 * this engagement has made.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AgNotificationScheduler {

    private final AgHealthEventRepository healthEventRepository;
    private final AgInventoryItemRepository inventoryItemRepository;
    private final NotificationService notificationService;
    private final TenantAdminRecipients tenantAdminRecipients;

    @Scheduled(cron = "0 30 9 * * *", zone = "Africa/Johannesburg")
    @Transactional
    public void runDailySweep() {
        sweepHealthEventsDue();
        sweepLowStockInventory();
    }

    private void sweepHealthEventsDue() {
        LocalDate today = LocalDate.now();
        List<AgHealthEvent> due = healthEventRepository.findDueAcrossTenants(today);
        int sent = 0;
        for (AgHealthEvent event : due) {
            try {
                notifyHealthEventDue(event);
                event.acknowledgeReminder();
                healthEventRepository.save(event);
                sent++;
            } catch (Exception e) {
                log.error("Failed to send health-event-due reminder for event={}: {}",
                        event.getId(), e.getMessage(), e);
            }
        }
        if (sent > 0) {
            log.info("Sent {} agriculture health-event-due reminder(s)", sent);
        }
    }

    private void sweepLowStockInventory() {
        List<AgInventoryItem> lowStock = inventoryItemRepository.findBelowReorderLevelAcrossTenants();
        int sent = 0;
        for (AgInventoryItem item : lowStock) {
            try {
                notifyLowStock(item);
                sent++;
            } catch (Exception e) {
                log.error("Failed to send low-stock alert for item={}: {}", item.getId(), e.getMessage(), e);
            }
        }
        if (sent > 0) {
            log.info("Sent {} agriculture low-stock alert(s)", sent);
        }
    }

    private void notifyHealthEventDue(AgHealthEvent event) {
        TenantId tenantId = event.getTenantId();
        List<Recipient> recipients = tenantAdminRecipients.resolveTenantAdmins(tenantId);
        if (recipients.isEmpty()) return;

        String subject = event.isForAnimal() ? "animal " + event.getAnimalId() : "group " + event.getGroupId();
        notificationService.send(NotificationRequest.builder()
                .tenantId(tenantId)
                .type(NotificationType.AG_HEALTH_EVENT_DUE)
                .title("Health event due: " + event.getEventType())
                .message(event.getEventType() + " is due (" + event.getNextDueDate() + ") for " + subject
                        + (event.getDescription() != null ? " — " + event.getDescription() : "") + ".")
                .actionUrl("/agriculture/health-events/" + event.getId())
                .sourceModule("agriculture")
                .sourceEntityId(event.getId().toString())
                .recipients(recipients)
                .build());
    }

    private void notifyLowStock(AgInventoryItem item) {
        TenantId tenantId = item.getTenantId();
        List<Recipient> recipients = tenantAdminRecipients.resolveTenantAdmins(tenantId);
        if (recipients.isEmpty()) return;

        notificationService.send(NotificationRequest.builder()
                .tenantId(tenantId)
                .type(NotificationType.AG_INVENTORY_LOW_STOCK)
                .title("Low stock: " + item.getItemName())
                .message(item.getItemName() + " is at " + item.getCurrentQuantity() + " " + item.getUnitOfMeasure()
                        + " — below its reorder level of " + item.getReorderLevel() + " " + item.getUnitOfMeasure() + ".")
                .actionUrl("/agriculture/inventory/" + item.getId())
                .sourceModule("agriculture")
                .sourceEntityId(item.getId().toString())
                .recipients(recipients)
                .build());
    }
}
