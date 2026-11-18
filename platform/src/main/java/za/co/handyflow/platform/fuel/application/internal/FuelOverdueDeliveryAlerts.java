package za.co.handyflow.platform.fuel.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import za.co.handyflow.platform.billing.FeatureGuard;
import za.co.handyflow.platform.fuel.domain.model.FuelDelivery;
import za.co.handyflow.platform.fuel.domain.model.FuelSettings;
import za.co.handyflow.platform.fuel.domain.repository.FuelDeliveryRepository;
import za.co.handyflow.platform.hr.application.HrFacade;
import za.co.handyflow.platform.notifications.application.Recipient;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Duration;
import java.time.Instant;
import java.util.*;

/**
 * Overdue delivery alerts with an escalation. A load is overdue once it is still open (scheduled or on the road) the company's
 * grace minutes after its scheduled time. Level 1 tells the assigned driver (or, with no driver on the app, dispatch straight
 * away). Level 2, the escalation, tells the dispatch recipients (topic DELIVERY_OVERDUE) the company's escalation minutes later if
 * the load is still open. Each level is sent once per load (the event key), whatever number of sweeps see it.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FuelOverdueDeliveryAlerts {

    static final Duration SHORTEST_GRACE = Duration.ofMinutes(15);

    private final FuelDeliveryRepository deliveries;
    private final FuelSettingsService settings;
    private final FeatureGuard featureGuard;
    private final HrFacade hr;
    private final FuelAlertRecipientService alertRecipients;
    private final FuelPushEventService pushEvents;

    @Scheduled(fixedDelay = 300_000, initialDelay = 120_000)
    public void sweep() { sweep(Instant.now()); }

    /** Public so a test can run it at a chosen time. Returns how many alerts were newly queued. */
    public int sweep(Instant now) {
        int queued = 0;
        Map<TenantId, FuelSettings> cfg = new HashMap<>();
        Map<TenantId, Boolean> hasModule = new HashMap<>();
        for (FuelDelivery d : deliveries.findOpenScheduledBeforeAnyTenant(now.minus(SHORTEST_GRACE))) {
            try {
                TenantId tenant = d.getTenantId();
                if (!hasModule.computeIfAbsent(tenant, t -> featureGuard.hasModuleFor(t, "fuel"))) continue;
                FuelSettings s = cfg.computeIfAbsent(tenant, settings::current);
                Instant overdueAt = d.getScheduledAt().plus(Duration.ofMinutes(s.getOverdueGraceMinutes()));
                if (now.isBefore(overdueAt)) continue;
                UUID driverUser = d.getDriverEmployeeId() == null ? null : hr.findEmployeeById(tenant, d.getDriverEmployeeId()).map(e -> e.userId()).orElse(null);
                Instant escalateAt = overdueAt.plus(Duration.ofMinutes(s.getOverdueEscalationMinutes()));
                boolean dispatchNow = driverUser == null || !now.isBefore(escalateAt);
                if (driverUser != null) {
                    queued += pushEvents.publish(tenant, FuelPushEventService.OVERDUE_DELIVERY, "OVERDUE:" + d.getId() + ":L1", List.of(driverUser), "Delivery overdue",
                            "A load is past its scheduled time. Open it to continue or report a problem.", Map.of("screen", "LoadDetail", "loadId", String.valueOf(d.getId())));
                }
                if (dispatchNow) {
                    List<UUID> to = alertRecipients.recipientsFor(tenant, FuelAlertRecipientService.DELIVERY_OVERDUE).stream().map(Recipient::userId).filter(Objects::nonNull)
                            .filter(u -> !u.equals(driverUser)).toList();
                    queued += pushEvents.publish(tenant, FuelPushEventService.OVERDUE_DELIVERY, "OVERDUE:" + d.getId() + ":L2", to, "Delivery overdue",
                            "A delivery is overdue" + (d.getVehicleReg() == null ? "." : " (" + d.getVehicleReg() + ")."), Map.of("screen", "Overview"));
                }
            } catch (RuntimeException e) {
                log.warn("[Fuel] Overdue alert failed for load {}: {}", d.getId(), e.toString());
            }
        }
        return queued;
    }
}
