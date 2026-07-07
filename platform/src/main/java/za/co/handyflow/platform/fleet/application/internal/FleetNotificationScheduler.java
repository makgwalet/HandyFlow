package za.co.handyflow.platform.fleet.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.fleet.domain.model.Driver;
import za.co.handyflow.platform.fleet.domain.model.Trip;
import za.co.handyflow.platform.fleet.domain.model.Vehicle;
import za.co.handyflow.platform.fleet.domain.model.VehicleStatus;
import za.co.handyflow.platform.fleet.domain.repository.DriverRepository;
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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * *** ACTION NEEDED IN YOUR NotificationType.java *** (Fleet section):
 * <pre>{@code
 * DRIVER_LICENSE_EXPIRING(WARNING, Set.of(IN_APP, EMAIL)),
 * DRIVER_PRDP_EXPIRING(WARNING, Set.of(IN_APP, EMAIL)),
 * }</pre>
 * This won't compile until both constants are added — same situation as
 * TRIP_RUNNING_LONG before it.
 * <p>
 * Driver compliance mirrors vehicle compliance exactly (same exact-day-match
 * idempotency at 60/30/7 days — see checkComplianceExpiry() for the
 * original rationale), with one addition: the driver themselves is notified
 * directly via {@code Recipient.external(...)}, not just tenant admins.
 * Drivers generally aren't platform users with logins, so there's no
 * {@code userId} to attach — external is exactly what that recipient type
 * exists for. If a driver has no email/phone on file, they're silently
 * skipped for that reason alone; tenant admins still get notified either way.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FleetNotificationScheduler {

    private static final int[] COMPLIANCE_ALERT_DAYS = {60, 30, 7};

    private final VehicleRepository vehicleRepository;
    private final TripRepository tripRepository;
    private final DriverRepository driverRepository;
    private final NotificationService notificationService;
    private final TenantAdminRecipients tenantAdminRecipients;

    @Value("${fleet.trip.long-running-hours:12}")
    private int longRunningTripHours;

    // ── Vehicle compliance document expiry — daily at 08:00 SAST ────────────

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
        if (expiry == null || !isAlertDay(expiry)) return 0;

        List<Recipient> recipients = recipientCache.computeIfAbsent(
                vehicle.getTenantId(), tenantAdminRecipients::resolveTenantAdmins);
        if (recipients.isEmpty()) return 0;

        long daysUntil = ChronoUnit.DAYS.between(LocalDate.now(), expiry);
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

    // ── Driver compliance document expiry — daily at 08:00 SAST ─────────────

    @Scheduled(cron = "0 0 8 * * *", zone = "Africa/Johannesburg")
    @Transactional(readOnly = true)
    public void checkDriverComplianceExpiry() {
        List<Driver> drivers = driverRepository.findAllActiveAcrossTenants();
        Map<TenantId, List<Recipient>> adminCache = new HashMap<>();
        int sent = 0;

        for (Driver d : drivers) {
            sent += checkDriverDocument(d, d.getLicenseExpiry(), NotificationType.DRIVER_LICENSE_EXPIRING,
                    "Driving licence", adminCache);
            if (d.isPrdpRequired()) {
                sent += checkDriverDocument(d, d.getPrdpExpiry(), NotificationType.DRIVER_PRDP_EXPIRING,
                        "PrDP", adminCache);
            }
        }
        log.info("Driver compliance sweep complete — drivers checked={} notifications sent={}", drivers.size(), sent);
    }

    private int checkDriverDocument(Driver driver, LocalDate expiry, NotificationType type, String docLabel,
                                    Map<TenantId, List<Recipient>> adminCache) {
        if (expiry == null || !isAlertDay(expiry)) return 0;

        List<Recipient> recipients = new ArrayList<>(
                adminCache.computeIfAbsent(driver.getTenantId(), tenantAdminRecipients::resolveTenantAdmins));
        if (driver.getEmail() != null || driver.getPhone() != null) {
            recipients.add(Recipient.external(driver.getFullName(), driver.getEmail(), driver.getPhone()));
        }
        if (recipients.isEmpty()) return 0;

        long daysUntil = ChronoUnit.DAYS.between(LocalDate.now(), expiry);
        notificationService.send(NotificationRequest.builder()
                .tenantId(driver.getTenantId())
                .type(type)
                .title(docLabel + " expiring in " + daysUntil + " days: " + driver.getFullName())
                .message(driver.getFullName() + "'s " + docLabel.toLowerCase()
                        + " expires on " + expiry + " (" + daysUntil + " days from now).")
                .actionUrl("/fleet/drivers/" + driver.getId())
                .sourceModule("fleet")
                .sourceEntityId(driver.getId().toString())
                .recipients(recipients)
                .build());
        return 1;
    }

    private boolean isAlertDay(LocalDate expiry) {
        long daysUntil = ChronoUnit.DAYS.between(LocalDate.now(), expiry);
        for (int d : COMPLIANCE_ALERT_DAYS) {
            if (daysUntil == d) return true;
        }
        return false;
    }

    // ── Long-running trip — every 30 minutes ─────────────────────────────────

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
