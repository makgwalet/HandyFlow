package za.co.handyflow.platform.fleet.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.fleet.domain.model.Battery;
import za.co.handyflow.platform.fleet.domain.model.BatteryStatus;
import za.co.handyflow.platform.fleet.domain.model.Vehicle;
import za.co.handyflow.platform.fleet.domain.repository.BatteryRepository;
import za.co.handyflow.platform.fleet.domain.repository.VehicleRepository;
import za.co.handyflow.platform.notifications.application.NotificationRequest;
import za.co.handyflow.platform.notifications.application.Recipient;
import za.co.handyflow.platform.notifications.application.TenantAdminRecipients;
import za.co.handyflow.platform.notifications.application.internal.NotificationService;
import za.co.handyflow.platform.notifications.domain.model.NotificationType;
import za.co.handyflow.platform.shared.TenantId;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * <p>
 * Daily 08:00 SAST: tells tenant admins when an INSTALLED battery's warranty is exactly 60, 30 or
 * 7 days from expiring — the same exact-day idempotency FleetNotificationScheduler uses for
 * compliance documents, so no "already sent" flag is needed. (Run once per day; a missed day
 * misses that alert.)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FleetBatteryScheduler {

    private static final int[] ALERT_DAYS = {60, 30, 7};

    private final BatteryRepository batteryRepository;
    private final VehicleRepository vehicleRepository;
    private final NotificationService notificationService;
    private final TenantAdminRecipients tenantAdminRecipients;

    @Scheduled(cron = "0 0 8 * * *", zone = "Africa/Johannesburg")
    @Transactional(readOnly = true)
    public void checkBatteryWarranties() {
        List<Battery> installed = batteryRepository.findByStatusWithWarranty(BatteryStatus.INSTALLED);
        Map<TenantId, List<Recipient>> recipientCache = new HashMap<>();
        LocalDate today = LocalDate.now();
        int sent = 0;

        for (Battery b : installed) {
            long daysUntil = ChronoUnit.DAYS.between(today, b.getWarrantyExpiry());
            if (!isAlertDay(daysUntil)) continue;

            List<Recipient> recipients = recipientCache.computeIfAbsent(
                    b.getTenantId(), tenantAdminRecipients::resolveTenantAdmins);
            if (recipients.isEmpty()) continue;

            String reg = vehicleRepository.findActiveById(b.getTenantId(), b.getCurrentVehicleId())
                    .map(Vehicle::getRegistration).orElse("a vehicle");
            notificationService.send(NotificationRequest.builder()
                    .tenantId(b.getTenantId())
                    .type(NotificationType.BATTERY_WARRANTY_EXPIRING)
                    .title("Battery warranty expiring in " + daysUntil + " days: " + b.getSerialNumber())
                    .message("The warranty on battery " + b.getSerialNumber() + " (" + b.getBrand()
                            + ") fitted to " + reg + " expires on " + b.getWarrantyExpiry() + " ("
                            + daysUntil + " days from now). Test it now while a claim is still possible.")
                    .actionUrl("/fleet/batteries/" + b.getId())
                    .sourceModule("fleet")
                    .sourceEntityId(b.getId().toString())
                    .recipients(recipients)
                    .build());
            sent++;
        }
        log.info("Battery warranty sweep complete — batteries checked={} notifications sent={}", installed.size(), sent);
    }

    private boolean isAlertDay(long daysUntil) {
        for (int d : ALERT_DAYS) {
            if (daysUntil == d) return true;
        }
        return false;
    }
}