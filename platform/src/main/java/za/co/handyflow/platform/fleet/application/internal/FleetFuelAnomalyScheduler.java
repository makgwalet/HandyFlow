package za.co.handyflow.platform.fleet.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.fleet.domain.model.FuelFillup;
import za.co.handyflow.platform.fleet.domain.model.Vehicle;
import za.co.handyflow.platform.fleet.domain.repository.FuelFillupRepository;
import za.co.handyflow.platform.fleet.domain.repository.VehicleRepository;
import za.co.handyflow.platform.notifications.application.NotificationRequest;
import za.co.handyflow.platform.notifications.application.Recipient;
import za.co.handyflow.platform.notifications.application.TenantAdminRecipients;
import za.co.handyflow.platform.notifications.application.internal.NotificationService;
import za.co.handyflow.platform.notifications.domain.model.NotificationType;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * *** ACTION NEEDED IN YOUR NotificationType.java *** (Fleet section):
 * <pre>{@code
 * FUEL_ANOMALY_DETECTED(WARNING, Set.of(IN_APP, EMAIL)),
 * }</pre>
 * This won't compile until the constant is added.
 * <p>
 * Daily 07:00 SAST: looks at the fill-ups recorded in the last 24 hours and, per vehicle, tells
 * tenant admins about abnormal consumption or theft / data-quality indicators that involve them —
 * one notification per vehicle, at most {@value #MAX_LINES} lines. Same trade-off as the other fleet
 * schedulers: the 24-hour window makes it idempotent without an "already sent" flag, but a day the
 * job doesn't run is a day of alerts missed (the alert feed endpoint still shows everything).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FleetFuelAnomalyScheduler {

    private static final int MAX_LINES = 5;

    private final FuelFillupRepository fuelFillupRepository;
    private final VehicleRepository vehicleRepository;
    private final FuelAnalyticsService analyticsService;
    private final NotificationService notificationService;
    private final TenantAdminRecipients tenantAdminRecipients;

    @Scheduled(cron = "0 0 7 * * *", zone = "Africa/Johannesburg")
    @Transactional(readOnly = true)
    public void checkRecentFillups() {
        Instant since = Instant.now().minus(24, ChronoUnit.HOURS);
        List<FuelFillup> recent = fuelFillupRepository.findCreatedSince(since);
        if (recent.isEmpty()) return;

        Map<UUID, Set<UUID>> newByVehicle = new HashMap<>();
        for (FuelFillup f : recent) {
            newByVehicle.computeIfAbsent(f.getVehicleId(), k -> new HashSet<>()).add(f.getId());
        }

        Map<TenantId, List<Recipient>> recipientCache = new HashMap<>();
        int sent = 0;
        for (Map.Entry<UUID, Set<UUID>> e : newByVehicle.entrySet()) {
            Vehicle vehicle = vehicleRepository.findById(e.getKey()).filter(v -> !v.isDeleted()).orElse(null);
            if (vehicle == null) continue;

            TenantId tenantId = vehicle.getTenantId();
            List<String> lines = analyticsService.detectAnomalies(tenantId, vehicle, e.getValue());
            if (lines.isEmpty()) continue;

            List<Recipient> recipients = recipientCache.computeIfAbsent(
                    tenantId, tenantAdminRecipients::resolveTenantAdmins);
            if (recipients.isEmpty()) continue;

            String body = String.join("\n• ", lines.subList(0, Math.min(MAX_LINES, lines.size())));
            if (lines.size() > MAX_LINES) body += "\n…and " + (lines.size() - MAX_LINES) + " more";
            notificationService.send(NotificationRequest.builder()
                    .tenantId(tenantId)
                    .type(NotificationType.FUEL_ANOMALY_DETECTED)
                    .title("Fuel anomaly: " + vehicle.getRegistration())
                    .message("Recent fuel records for " + vehicle.getRegistration()
                            + " need a look:\n• " + body
                            + "\nThese are indicators, not proof — check the receipts and odometer readings.")
                    .actionUrl("/fleet/vehicles/" + vehicle.getId())
                    .sourceModule("fleet")
                    .sourceEntityId(vehicle.getId().toString())
                    .recipients(recipients)
                    .build());
            sent++;
        }
        log.info("Fuel anomaly sweep complete — vehicles with new fill-ups={} notifications sent={}",
                newByVehicle.size(), sent);
    }
}