package za.co.handyflow.platform.fleet.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.fleet.domain.model.Trip;
import za.co.handyflow.platform.fleet.domain.model.Vehicle;
import za.co.handyflow.platform.fleet.domain.model.VehicleStatus;
import za.co.handyflow.platform.fleet.domain.repository.TripRepository;
import za.co.handyflow.platform.fleet.domain.repository.VehicleRepository;
import za.co.handyflow.platform.notifications.application.NotificationRequest;
import za.co.handyflow.platform.notifications.application.Recipient;
import za.co.handyflow.platform.notifications.application.TenantAdminRecipients;
import za.co.handyflow.platform.notifications.application.internal.NotificationService;
import za.co.handyflow.platform.notifications.domain.model.NotificationType;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * *** THIS IS THE FIX FOR THE CONFIRMED FALSE PROMISE ***
 * <p>
 * VehiclesTab.tsx tells the user: "You will be alerted 60, 30, and 7 days
 * before any document expires." Before this class existed, nothing backed
 * that claim — isLicenceExpiringSoon()/isRoadworthyExpiringSoon() only drove
 * UI badges someone has to actively look at. This scheduler is what makes
 * the sentence true.
 * <p>
 * IDEMPOTENCY: fires on an EXACT day match (daysUntil == 60, 30, or 7), not
 * a range (<= 60) — otherwise a vehicle sitting in the 1-59 day window would
 * get re-notified every single day the scheduler runs. This is the same
 * pattern already established in
 * {@code za.co.handyflow.platform.shared.NotificationScheduler
 * .sendPilotCountdownReminders()} (day-count IN-list match) — reused here
 * rather than inventing a second idempotency convention for the same kind
 * of problem.
 * <p>
 * WHY a fleet-specific scheduler instead of adding methods to the existing
 * shared NotificationScheduler? That class talks to the database via raw
 * JdbcTemplate and calls EmailService.send() directly — a simpler,
 * email-only mechanism that predates the full NotificationService pipeline
 * (in-app rows, per-user channel preferences, SMS support). Fleet's
 * VEHICLE_LICENCE_EXPIRING/ROADWORTHY/INSURANCE/SERVICE_DUE/BREAKDOWN
 * NotificationType constants were already defined with IN_APP+EMAIL default
 * channels — clearly intended for the full pipeline, not the raw-SQL one —
 * so this scheduler is built on JPA repositories + NotificationService to
 * match what those constants were designed for.
 * <p>
 * *** ACTION NEEDED IN YOUR NotificationType.java ***: this class uses
 * {@code NotificationType.TRIP_RUNNING_LONG}, which does not exist yet in
 * the catalogue you showed me. Add it to the Fleet section:
 * <pre>{@code
 * TRIP_RUNNING_LONG(WARNING, Set.of(IN_APP, EMAIL)),
 * }</pre>
 * This won't compile until that constant is added.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FleetNotificationScheduler {

    private static final int[] COMPLIANCE_ALERT_DAYS = {60, 30, 7};

    private final VehicleRepository vehicleRepository;
    private final TripRepository tripRepository;
    private final NotificationService notificationService;
    private final TenantAdminRecipients tenantAdminRecipients;

    @Value("${fleet.trip.long-running-hours:12}")
    private int longRunningTripHours;

    // ── Compliance document expiry — daily at 08:00 SAST ────────────────────
    // Same time-of-day as the existing quote-expiry/pilot-countdown jobs in
    // shared.NotificationScheduler, for one predictable "when do fleet/admin
    // emails land" expectation across the whole platform.

    @Scheduled(cron = "0 0 8 * * *", zone = "Africa/Johannesburg")
    @Transactional(readOnly = true)
    public void checkComplianceExpiry() {
        List<Vehicle> vehicles = vehicleRepository.findAllActiveExcludingStatus(VehicleStatus.RETIRED);
        Map<TenantId, List<Recipient>> recipientCache = new HashMap<>();
        int sent = 0;

        for (Vehicle v : vehicles) {
            sent += checkDocument(v, v.getLicenceDiscExpiry(), NotificationType.VEHICLE_LICENCE_EXPIRING,
                    "Licence disc", recipientCache);
            sent += checkDocument(v, v.getRoadworthyExpiry(), NotificationType.VEHICLE_ROADWORTHY_EXPIRING,
                    "Roadworthy certificate", recipientCache);
            sent += checkDocument(v, v.getInsuranceExpiry(), NotificationType.VEHICLE_INSURANCE_EXPIRING,
                    "Insurance", recipientCache);
        }
        log.info("Fleet compliance sweep complete — vehicles checked={} notifications sent={}", vehicles.size(), sent);
    }

    private int checkDocument(Vehicle vehicle, LocalDate expiry, NotificationType type, String docLabel,
                              Map<TenantId, List<Recipient>> recipientCache) {
        if (expiry == null) return 0;
        long daysUntil = ChronoUnit.DAYS.between(LocalDate.now(), expiry);
        boolean isAlertDay = false;
        for (int d : COMPLIANCE_ALERT_DAYS) {
            if (daysUntil == d) { isAlertDay = true; break; }
        }
        if (!isAlertDay) return 0;

        List<Recipient> recipients = recipientCache.computeIfAbsent(
                vehicle.getTenantId(), tenantAdminRecipients::resolveTenantAdmins);
        if (recipients.isEmpty()) return 0;

        notificationService.send(NotificationRequest.builder()
                .tenantId(vehicle.getTenantId())
                .type(type)
                .title(docLabel + " expiring in " + daysUntil + " days: " + vehicle.getRegistration())
                .message(vehicle.getRegistration() + "'s " + docLabel.toLowerCase()
                        + " expires on " + expiry + " (" + daysUntil + " days from now).")
                .actionUrl("/fleet/vehicles/" + vehicle.getId())
                .sourceModule("fleet")
                .sourceEntityId(vehicle.getId().toString())
                .recipients(recipients)
                .build());
        return 1;
    }

    // ── Long-running trip — every 30 minutes ─────────────────────────────────
    // A shorter interval than the daily compliance sweep because a forgotten
    // "End Trip" click is a data-quality problem best caught within hours,
    // not discovered a day later.

    @Scheduled(cron = "0 */30 * * * *")
    @Transactional
    public void checkLongRunningTrips() {
        Instant cutoff = Instant.now().minus(longRunningTripHours, ChronoUnit.HOURS);
        List<Trip> longRunning = tripRepository.findLongRunningUnalertedTrips(cutoff);
        if (longRunning.isEmpty()) return;

        Map<TenantId, List<Recipient>> recipientCache = new HashMap<>();
        int sent = 0;

        for (Trip trip : longRunning) {
            List<Recipient> recipients = recipientCache.computeIfAbsent(
                    trip.getTenantId(), tenantAdminRecipients::resolveTenantAdmins);

            // Mark alerted regardless of whether anyone was actually
            // notified — otherwise a tenant with zero resolvable recipients
            // would have this trip re-evaluated (and logged) every 30
            // minutes forever instead of once.
            trip.markLongRunningAlertSent();
            tripRepository.save(trip);

            if (recipients.isEmpty()) continue;

            long hoursRunning = ChronoUnit.HOURS.between(trip.getStartAt(), Instant.now());
            notificationService.send(NotificationRequest.builder()
                    .tenantId(trip.getTenantId())
                    .type(NotificationType.TRIP_RUNNING_LONG)
                    .title("Trip still active after " + hoursRunning + " hours")
                    .message("A trip started " + hoursRunning + " hours ago"
                            + (trip.getDriverName() != null ? " by " + trip.getDriverName() : "")
                            + " has not been ended. If this is a genuine long trip, ignore this — otherwise "
                            + "the driver may have forgotten to end it.")
                    .actionUrl("/fleet/trips")
                    .sourceModule("fleet")
                    .sourceEntityId(trip.getId().toString())
                    .recipients(recipients)
                    .build());
            sent++;
        }
        log.info("Long-running trip sweep complete — flagged={} notifications sent={}", longRunning.size(), sent);
    }
}
