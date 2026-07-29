package za.co.handyflow.platform.fuel.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.fuel.domain.model.FuelDelivery;
import za.co.handyflow.platform.fuel.domain.repository.FuelDeliveryRepository;
import za.co.handyflow.platform.notifications.application.NotificationRequest;
import za.co.handyflow.platform.notifications.application.Recipient;
import za.co.handyflow.platform.notifications.application.TenantAdminRecipients;
import za.co.handyflow.platform.notifications.application.internal.NotificationService;
import za.co.handyflow.platform.notifications.domain.model.NotificationType;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The two time-driven fuel-delivery notifications the audit flagged as
 * missing — "no pending-delivery reminder... nothing reminds staff as a
 * scheduled delivery's date approaches or if it's overdue." Nothing
 * request-driven triggers either one (the passage of time does), so — same
 * division of responsibility as FleetNotificationScheduler and
 * TasksNotificationScheduler — this is a scheduler that queries, resolves
 * recipients, and delegates to NotificationService.send().
 * <p>
 * Recipients are resolved via TenantAdminRecipients rather than the
 * delivery's own driverName: FuelDelivery captures a driver's name but no
 * contact details (email/phone) to notify them directly, so — same
 * reasoning as the other Fuel alerts already wired in — this goes to
 * tenant admins as the generic catch-all. If drivers get their own
 * contact fields later, this could add Recipient.external(...) for the
 * driver the same way FleetNotificationScheduler does for vehicle drivers.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FuelDeliveryNotificationScheduler {

    private final FuelDeliveryRepository deliveryRepository;
    private final NotificationService notificationService;
    private final TenantAdminRecipients tenantAdminRecipients;

    @Value("${fuel.delivery.reminder-hours:24}")
    private int reminderWindowHours;

    // ── Upcoming delivery reminder — daily at 07:00 SAST ────────────────────

    @Scheduled(cron = "0 0 7 * * *", zone = "Africa/Johannesburg")
    @Transactional
    public void checkUpcomingDeliveries() {
        Instant now       = Instant.now();
        Instant windowEnd = now.plus(reminderWindowHours, ChronoUnit.HOURS);
        List<FuelDelivery> upcoming = deliveryRepository.findUpcomingNeedingReminder(now, windowEnd);

        Map<TenantId, List<Recipient>> recipientCache = new HashMap<>();
        int sent = 0;

        for (FuelDelivery delivery : upcoming) {
            delivery.markReminderSent();
            deliveryRepository.save(delivery);

            List<Recipient> recipients = recipientCache.computeIfAbsent(
                    delivery.getTenantId(), tenantAdminRecipients::resolveTenantAdmins);
            if (recipients.isEmpty()) continue;

            notificationService.send(NotificationRequest.builder()
                    .tenantId(delivery.getTenantId())
                    .type(NotificationType.FUEL_DELIVERY_UPCOMING)
                    .title("Fuel delivery scheduled soon")
                    .message(deliveryDescription(delivery) + " is scheduled for "
                            + delivery.getScheduledAt() + ".")
                    .actionUrl("/fuel/deliveries/" + delivery.getId())
                    .sourceModule("fuel")
                    .sourceEntityId(delivery.getId().toString())
                    .recipients(recipients)
                    .build());
            sent++;
        }
        log.info("Fuel delivery reminder sweep complete — checked={} notifications sent={}", upcoming.size(), sent);
    }

    // ── Overdue delivery alert — daily at 08:00 SAST ────────────────────────

    @Scheduled(cron = "0 0 8 * * *", zone = "Africa/Johannesburg")
    @Transactional
    public void checkOverdueDeliveries() {
        List<FuelDelivery> overdue = deliveryRepository.findOverdueNeedingAlert(Instant.now());

        Map<TenantId, List<Recipient>> recipientCache = new HashMap<>();
        int sent = 0;

        for (FuelDelivery delivery : overdue) {
            delivery.markOverdueAlertSent();
            deliveryRepository.save(delivery);

            List<Recipient> recipients = recipientCache.computeIfAbsent(
                    delivery.getTenantId(), tenantAdminRecipients::resolveTenantAdmins);
            if (recipients.isEmpty()) continue;

            notificationService.send(NotificationRequest.builder()
                    .tenantId(delivery.getTenantId())
                    .type(NotificationType.FUEL_DELIVERY_OVERDUE)
                    .title("Fuel delivery overdue")
                    .message(deliveryDescription(delivery) + " was scheduled for "
                            + delivery.getScheduledAt() + " and still hasn't been marked delivered.")
                    .actionUrl("/fuel/deliveries/" + delivery.getId())
                    .sourceModule("fuel")
                    .sourceEntityId(delivery.getId().toString())
                    .recipients(recipients)
                    .build());
            sent++;
        }
        log.info("Fuel delivery overdue sweep complete — flagged={} notifications sent={}", overdue.size(), sent);
    }

    private String deliveryDescription(FuelDelivery delivery) {
        StringBuilder sb = new StringBuilder(delivery.getLitresOrdered() + "L " + delivery.getFuelType() + " delivery");
        if (delivery.getDriverName() != null) sb.append(" (driver: ").append(delivery.getDriverName()).append(")");
        return sb.toString();
    }
}