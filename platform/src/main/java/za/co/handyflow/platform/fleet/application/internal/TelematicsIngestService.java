package za.co.handyflow.platform.fleet.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import za.co.handyflow.platform.fleet.domain.model.Geofence;
import za.co.handyflow.platform.fleet.domain.model.GeofenceState;
import za.co.handyflow.platform.fleet.domain.model.TelematicsDevice;
import za.co.handyflow.platform.fleet.domain.model.TelematicsEvent;
import za.co.handyflow.platform.fleet.domain.model.TelematicsEventSource;
import za.co.handyflow.platform.fleet.domain.model.TelematicsEventType;
import za.co.handyflow.platform.fleet.domain.model.TelematicsIngestKey;
import za.co.handyflow.platform.fleet.domain.model.TelematicsSettings;
import za.co.handyflow.platform.fleet.domain.model.TelematicsSeverity;
import za.co.handyflow.platform.fleet.domain.model.Trip;
import za.co.handyflow.platform.fleet.domain.model.Vehicle;
import za.co.handyflow.platform.fleet.domain.model.VehicleLastPosition;
import za.co.handyflow.platform.fleet.domain.repository.GeofenceRepository;
import za.co.handyflow.platform.fleet.domain.repository.GeofenceStateRepository;
import za.co.handyflow.platform.fleet.domain.repository.TelematicsDeviceRepository;
import za.co.handyflow.platform.fleet.domain.repository.TelematicsEventRepository;
import za.co.handyflow.platform.fleet.domain.repository.TelematicsIngestKeyRepository;
import za.co.handyflow.platform.fleet.domain.repository.TelematicsSettingsRepository;
import za.co.handyflow.platform.fleet.domain.repository.TripRepository;
import za.co.handyflow.platform.fleet.domain.repository.VehicleLastPositionRepository;
import za.co.handyflow.platform.fleet.domain.repository.VehicleRepository;
import za.co.handyflow.platform.fleet.dto.TelematicsIngestRequest;
import za.co.handyflow.platform.fleet.dto.TelematicsIngestRequest.EventItem;
import za.co.handyflow.platform.fleet.dto.TelematicsIngestRequest.PositionItem;
import za.co.handyflow.platform.fleet.dto.TelematicsIngestResponse;
import za.co.handyflow.platform.fleet.dto.TelematicsIngestResponse.Rejection;
import za.co.handyflow.platform.notifications.application.NotificationRequest;
import za.co.handyflow.platform.notifications.application.Recipient;
import za.co.handyflow.platform.notifications.application.TenantAdminRecipients;
import za.co.handyflow.platform.notifications.application.internal.NotificationService;
import za.co.handyflow.platform.notifications.domain.model.NotificationType;
import za.co.handyflow.platform.shared.TenantId;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * <p>
 * Receives GPS positions and native device events from any provider, authenticated by a per-tenant
 * API key, and runs the rules over them:
 * <ul>
 *   <li><b>Speeding</b> — a fix above the applicable limit plus tolerance (the lowest limit among
 *       the default and any geofence the fix is inside). One event per vehicle per cooldown period,
 *       so a long stretch of speeding is one event, not hundreds.</li>
 *   <li><b>Excessive idling</b> — ignition on and stationary for the configured time; once per idle
 *       spell. Needs the provider to send {@code ignitionOn}; without it there are no idling events.</li>
 *   <li><b>Geofence enter / exit</b> — on a change of inside/outside for fences set to alert. A
 *       vehicle's first sighting only records its state; it cannot "enter" what it was already in.</li>
 * </ul>
 * Harsh braking / acceleration / cornering and device-unplugged cannot be derived from GPS fixes; they
 * arrive as provider events through the same endpoint.
 * <p>
 * Behaviour worth knowing:
 * <ul>
 *   <li>Positions are stored in bulk ({@code ON CONFLICT DO NOTHING}), but rules only run for a fix
 *       newer than the vehicle's last evaluated one — a late, re-sent or back-filled batch is stored
 *       and never raises events retroactively or corrupts running state.</li>
 *   <li>Each event is attributed to the driver of the vehicle's trip at that moment, else the
 *       vehicle's assigned driver. That attribution feeds the driver Safety score.</li>
 *   <li>One bad item never fails a batch; it is reported in the response and the rest go through.</li>
 *   <li>This endpoint runs without a user session. The tenant comes from the API key; configure the
 *       security filter chain to let the path through (see TelematicsIngestController), and note
 *       that FeatureGuard's module check, which needs a tenant context, is not applied here.</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TelematicsIngestService {

    private static final int MAX_ITEMS = 1000;
    private static final int MAX_REJECTIONS_REPORTED = 50;
    private static final Duration MAX_AGE = Duration.ofDays(30);
    private static final Duration MAX_FUTURE = Duration.ofMinutes(5);
    private static final int MAX_NOTIFICATION_LINES = 5;

    private static final String INSERT_POSITION =
            "INSERT INTO fleet_telematics_positions (id, tenant_id, vehicle_id, recorded_at, latitude, longitude, "
                    + "speed_kph, heading, odometer_km, ignition_on, received_at) "
                    + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) ON CONFLICT (vehicle_id, recorded_at) DO NOTHING";

    private final TelematicsIngestKeyRepository keyRepository;
    private final TelematicsDeviceRepository deviceRepository;
    private final VehicleRepository vehicleRepository;
    private final VehicleLastPositionRepository lastPositionRepository;
    private final GeofenceRepository geofenceRepository;
    private final GeofenceStateRepository geofenceStateRepository;
    private final TelematicsEventRepository eventRepository;
    private final TelematicsSettingsRepository settingsRepository;
    private final TripRepository tripRepository;
    private final JdbcTemplate jdbc;
    private final NotificationService notificationService;
    private final TenantAdminRecipients tenantAdminRecipients;

    /** A validated GPS fix, resolved to a vehicle. */
    private record Pos(UUID vehicleId, Instant at, double lat, double lng, Double speed, Integer heading,
                       Double odometerKm, Boolean ignition) {}

    private record DriverRef(UUID driverId, UUID tripId) {}

    // ── Entry point ──────────────────────────────────────────────────────

    @Transactional
    public TelematicsIngestResponse ingest(String rawKey, TelematicsIngestRequest req) {
        TenantId tenantId = authenticate(rawKey);

        List<PositionItem> positions = req.positions() != null ? req.positions() : List.of();
        List<EventItem> events = req.events() != null ? req.events() : List.of();
        if (positions.size() + events.size() > MAX_ITEMS) {
            throw new IllegalArgumentException("At most " + MAX_ITEMS + " items (positions + events) per request");
        }
        if (positions.isEmpty() && events.isEmpty()) {
            return new TelematicsIngestResponse(0, 0, 0, 0, 0, List.of(), false);
        }

        Instant now = Instant.now();
        List<Rejection> rejections = new ArrayList<>();
        Map<String, TelematicsDevice> devices = deviceRepository.findAllForTenant(tenantId).stream()
                .filter(TelematicsDevice::isActive)
                .collect(Collectors.toMap(d -> d.getExternalId().toLowerCase(Locale.ROOT), Function.identity(), (a, b) -> a));
        Map<UUID, Vehicle> vehicles = new HashMap<>();

        // 1. Validate and resolve positions.
        List<Pos> accepted = new ArrayList<>();
        for (int i = 0; i < positions.size(); i++) {
            PositionItem p = positions.get(i);
            String problem = timeProblem(p.recordedAt(), now);
            Vehicle v = null;
            TelematicsDevice d = devices.get(p.deviceId().trim().toLowerCase(Locale.ROOT));
            if (problem == null) {
                if (d == null) problem = "Unknown or inactive device '" + p.deviceId() + "'";
                else {
                    v = vehicleFor(tenantId, d, vehicles);
                    if (v == null) problem = "The device's vehicle no longer exists";
                }
            }
            if (problem != null) {
                rejections.add(new Rejection("position", i, problem));
                continue;
            }
            d.seen(p.recordedAt());
            accepted.add(new Pos(v.getId(), p.recordedAt(), p.latitude(), p.longitude(), p.speedKph(),
                    p.heading(), p.odometerKm(), p.ignitionOn()));
        }

        // 2. Store every accepted position in bulk.
        if (!accepted.isEmpty()) {
            Timestamp received = Timestamp.from(now);
            jdbc.batchUpdate(INSERT_POSITION, accepted, 500, (ps, pos) -> {
                ps.setObject(1, UUID.randomUUID());
                ps.setObject(2, tenantId.getValue());
                ps.setObject(3, pos.vehicleId());
                ps.setTimestamp(4, Timestamp.from(pos.at()));
                ps.setDouble(5, pos.lat());
                ps.setDouble(6, pos.lng());
                ps.setObject(7, pos.speed(), Types.DOUBLE);
                ps.setObject(8, pos.heading(), Types.INTEGER);
                ps.setObject(9, pos.odometerKm(), Types.DOUBLE);
                ps.setObject(10, pos.ignition(), Types.BOOLEAN);
                ps.setTimestamp(11, received);
            });
        }

        // 3. Run the rules, vehicle by vehicle, oldest fix first.
        List<TelematicsEvent> newEvents = new ArrayList<>();
        int derived = runRules(tenantId, accepted, vehicles, newEvents);

        // 4. Native provider events.
        int providerAccepted = 0;
        int providerDuplicate = 0;
        Set<String> seenInBatch = new HashSet<>();
        for (int i = 0; i < events.size(); i++) {
            EventItem e = events.get(i);
            String problem = timeProblem(e.occurredAt(), now);
            Vehicle v = null;
            TelematicsDevice d = devices.get(e.deviceId().trim().toLowerCase(Locale.ROOT));
            if (problem == null) {
                if (d == null) problem = "Unknown or inactive device '" + e.deviceId() + "'";
                else {
                    v = vehicleFor(tenantId, d, vehicles);
                    if (v == null) problem = "The device's vehicle no longer exists";
                }
            }
            if (problem != null) {
                rejections.add(new Rejection("event", i, problem));
                continue;
            }
            d.seen(e.occurredAt());
            String dedupeKey = v.getId() + "|" + e.type() + "|" + e.occurredAt();
            if (!seenInBatch.add(dedupeKey) || eventRepository.existsDuplicate(v.getId(), e.type(), e.occurredAt())) {
                providerDuplicate++;
                continue;
            }
            DriverRef driver = resolveDriver(v, e.occurredAt());
            TelematicsSeverity severity = e.severity() != null ? e.severity()
                    : (e.type() == TelematicsEventType.DEVICE_UNPLUGGED ? TelematicsSeverity.ALERT : TelematicsSeverity.WARNING);
            newEvents.add(TelematicsEvent.record(tenantId, v.getId(), driver.driverId(), driver.tripId(), e.type(),
                    severity, e.occurredAt(), e.latitude(), e.longitude(), e.value(), null, null,
                    e.details(), null, TelematicsEventSource.PROVIDER));
            providerAccepted++;
        }

        eventRepository.saveAll(newEvents);
        notifyNotable(tenantId, newEvents, vehicles);

        int rejectedCount = rejections.size();
        boolean truncated = rejectedCount > MAX_REJECTIONS_REPORTED;
        log.info("Telematics ingest tenant={} positions={} derivedEvents={} providerEvents={} rejected={}",
                tenantId, accepted.size(), derived, providerAccepted, rejectedCount);
        return new TelematicsIngestResponse(accepted.size(), providerAccepted, providerDuplicate, derived,
                rejectedCount, truncated ? rejections.subList(0, MAX_REJECTIONS_REPORTED) : rejections, truncated);
    }

    // ── Rules ────────────────────────────────────────────────────────────

    private int runRules(TenantId tenantId, List<Pos> accepted, Map<UUID, Vehicle> vehicles, List<TelematicsEvent> out) {
        if (accepted.isEmpty()) return 0;

        TelematicsSettings settings = settingsRepository.findByTenantId(tenantId)
                .orElseGet(() -> TelematicsSettings.defaults(tenantId));
        List<Geofence> fences = geofenceRepository.findActive(tenantId);
        Map<UUID, double[][]> polygons = new HashMap<>();
        for (Geofence g : fences) polygons.put(g.getId(), GeoMath.parsePolygon(g.getPolygonJson()));

        Map<UUID, List<Pos>> byVehicle = accepted.stream().collect(Collectors.groupingBy(Pos::vehicleId));
        Map<UUID, VehicleLastPosition> lastByVehicle = lastPositionRepository.findByVehicleIds(byVehicle.keySet())
                .stream().collect(Collectors.toMap(VehicleLastPosition::getVehicleId, Function.identity()));
        Map<String, GeofenceState> states = fences.isEmpty() ? new HashMap<>()
                : geofenceStateRepository.findByVehicleIds(byVehicle.keySet()).stream()
                .collect(Collectors.toMap(s -> stateKey(s.getVehicleId(), s.getGeofenceId()), Function.identity(), (a, b) -> a));

        int derived = 0;
        Duration cooldown = Duration.ofMinutes(settings.getSpeedingCooldownMinutes());
        Duration idleThreshold = Duration.ofMinutes(settings.getIdlingThresholdMinutes());

        for (Map.Entry<UUID, List<Pos>> entry : byVehicle.entrySet()) {
            Vehicle vehicle = vehicles.get(entry.getKey());
            VehicleLastPosition last = lastByVehicle.get(entry.getKey());
            List<Pos> ordered = new ArrayList<>(entry.getValue());
            ordered.sort(Comparator.comparing(Pos::at));

            for (Pos p : ordered) {
                // Only a fix newer than the last evaluated one drives the rules; older ones are just stored.
                if (last != null && !p.at().isAfter(last.getRecordedAt())) continue;

                boolean isNew = last == null;
                if (isNew) {
                    last = VehicleLastPosition.first(tenantId, p.vehicleId(), p.lat(), p.lng(), p.speed(),
                            p.heading(), p.odometerKm(), p.ignition(), p.at());
                    lastByVehicle.put(p.vehicleId(), last);
                }

                // Speeding
                double limit = settings.getDefaultSpeedLimitKph();
                Geofence limiting = null;
                for (Geofence g : fences) {
                    if (g.getSpeedLimitKph() != null && g.getSpeedLimitKph() < limit
                            && GeoMath.contains(g, polygons.get(g.getId()), p.lat(), p.lng())) {
                        limit = g.getSpeedLimitKph();
                        limiting = g;
                    }
                }
                if (p.speed() != null && p.speed() > limit + settings.getSpeedingToleranceKph()
                        && (last.getLastSpeedingEventAt() == null
                        || p.at().isAfter(last.getLastSpeedingEventAt().plus(cooldown)))) {
                    double over = p.speed() - limit;
                    TelematicsSeverity severity = over >= settings.getAlertOverspeedKph()
                            ? TelematicsSeverity.ALERT : TelematicsSeverity.WARNING;
                    String where = limiting != null ? " in " + limiting.getName() : "";
                    out.add(derivedEvent(tenantId, vehicle, p, TelematicsEventType.SPEEDING, severity, p.speed(),
                            limit, null, String.format(Locale.ROOT, "%.0f km/h in a %.0f km/h zone%s", p.speed(), limit, where),
                            limiting != null ? limiting.getId() : null));
                    last.markSpeedingReported(p.at());
                    derived++;
                }

                // Idling
                Duration idleFor = last.trackIdle(p.ignition(), p.speed(), p.at(), idleThreshold);
                if (idleFor != null) {
                    out.add(derivedEvent(tenantId, vehicle, p, TelematicsEventType.EXCESSIVE_IDLING,
                            TelematicsSeverity.WARNING, null, null, (int) idleFor.getSeconds(),
                            "Engine running while stationary for " + idleFor.toMinutes() + " minutes", null));
                    derived++;
                }

                // Geofence transitions
                for (Geofence g : fences) {
                    boolean inside = GeoMath.contains(g, polygons.get(g.getId()), p.lat(), p.lng());
                    String key = stateKey(p.vehicleId(), g.getId());
                    GeofenceState state = states.get(key);
                    if (state == null) {
                        GeofenceState fresh = GeofenceState.first(p.vehicleId(), g.getId(), inside, p.at());
                        states.put(key, fresh);
                        geofenceStateRepository.save(fresh);
                    } else if (state.isInside() != inside) {
                        state.moveTo(inside, p.at());
                        if (inside && g.isAlertOnEnter()) {
                            out.add(derivedEvent(tenantId, vehicle, p, TelematicsEventType.GEOFENCE_ENTER,
                                    TelematicsSeverity.WARNING, null, null, null, "Entered " + g.getName(), g.getId()));
                            derived++;
                        } else if (!inside && g.isAlertOnExit()) {
                            out.add(derivedEvent(tenantId, vehicle, p, TelematicsEventType.GEOFENCE_EXIT,
                                    TelematicsSeverity.WARNING, null, null, null, "Left " + g.getName(), g.getId()));
                            derived++;
                        }
                    }
                }

                if (!isNew) {
                    last.update(p.lat(), p.lng(), p.speed(), p.heading(), p.odometerKm(), p.ignition(), p.at());
                }
            }
            if (last != null) lastPositionRepository.save(last);
        }

        // Defensive: drop derived events that repeat one already on file (re-sent batches shouldn't, but the index would reject them).
        out.removeIf(e -> e.getSource() == TelematicsEventSource.DERIVED
                && eventRepository.existsDuplicate(e.getVehicleId(), e.getType(), e.getOccurredAt()));
        return (int) out.stream().filter(e -> e.getSource() == TelematicsEventSource.DERIVED).count();
    }

    private TelematicsEvent derivedEvent(TenantId tenantId, Vehicle vehicle, Pos p, TelematicsEventType type,
                                         TelematicsSeverity severity, Double value, Double limit,
                                         Integer durationSeconds, String details, UUID geofenceId) {
        DriverRef driver = resolveDriver(vehicle, p.at());
        return TelematicsEvent.record(tenantId, p.vehicleId(), driver.driverId(), driver.tripId(), type, severity,
                p.at(), p.lat(), p.lng(), value, limit, durationSeconds, details, geofenceId,
                TelematicsEventSource.DERIVED);
    }

    /** The driver of the vehicle's trip at that moment; failing that, the vehicle's assigned driver. */
    private DriverRef resolveDriver(Vehicle vehicle, Instant at) {
        List<Trip> trips = tripRepository.findTripsAt(vehicle.getId(), at);
        if (!trips.isEmpty()) {
            Trip t = trips.get(0);
            return new DriverRef(t.getDriverId() != null ? t.getDriverId() : vehicle.getAssignedDriverId(), t.getId());
        }
        return new DriverRef(vehicle.getAssignedDriverId(), null);
    }

    // ── Notifications ────────────────────────────────────────────────────

    /** One notification per vehicle per batch for the events that warrant interrupting someone: alerts, geofence crossings, unplugged devices. */
    private void notifyNotable(TenantId tenantId, List<TelematicsEvent> events, Map<UUID, Vehicle> vehicles) {
        Map<UUID, List<TelematicsEvent>> notable = events.stream()
                .filter(e -> e.getSeverity() == TelematicsSeverity.ALERT
                        || e.getType() == TelematicsEventType.GEOFENCE_ENTER
                        || e.getType() == TelematicsEventType.GEOFENCE_EXIT
                        || e.getType() == TelematicsEventType.DEVICE_UNPLUGGED)
                .collect(Collectors.groupingBy(TelematicsEvent::getVehicleId));
        if (notable.isEmpty()) return;

        List<Recipient> recipients = tenantAdminRecipients.resolveTenantAdmins(tenantId);
        if (recipients.isEmpty()) return;

        for (Map.Entry<UUID, List<TelematicsEvent>> e : notable.entrySet()) {
            Vehicle v = vehicles.get(e.getKey());
            String reg = v != null ? v.getRegistration() : "a vehicle";
            List<TelematicsEvent> list = e.getValue();
            String lines = list.stream().limit(MAX_NOTIFICATION_LINES)
                    .map(ev -> "• " + ev.getType() + " at " + ev.getOccurredAt()
                            + (ev.getDetails() != null ? " — " + ev.getDetails() : ""))
                    .collect(Collectors.joining("\n"));
            if (list.size() > MAX_NOTIFICATION_LINES) lines += "\n…and " + (list.size() - MAX_NOTIFICATION_LINES) + " more";
            notificationService.send(NotificationRequest.builder()
                    .tenantId(tenantId)
                    .type(NotificationType.TELEMATICS_ALERT)
                    .title("Vehicle alert: " + reg)
                    .message(reg + ":\n" + lines)
                    .actionUrl("/fleet/vehicles/" + e.getKey())
                    .sourceModule("fleet")
                    .sourceEntityId(e.getKey().toString())
                    .recipients(recipients)
                    .build());
        }
    }

    // ── Authentication & helpers ─────────────────────────────────────────

    /**
     * Resolves the tenant from the presented API key (compared by SHA-256 hash). A missing, unknown or
     * revoked key is a 401 and tells the caller nothing about which.
     */
    TenantId authenticate(String rawKey) {
        if (rawKey == null || rawKey.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "A valid API key is required");
        }
        TelematicsIngestKey key = keyRepository.findByKeyHash(hashKey(rawKey.trim()))
                .filter(TelematicsIngestKey::isActive)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "A valid API key is required"));
        key.touch(Instant.now());
        return key.getTenantId();
    }

    static String hashKey(String rawKey) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(rawKey.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(64);
            for (byte b : digest) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }

    private Vehicle vehicleFor(TenantId tenantId, TelematicsDevice device, Map<UUID, Vehicle> cache) {
        if (cache.containsKey(device.getVehicleId())) return cache.get(device.getVehicleId());
        Vehicle v = vehicleRepository.findActiveById(tenantId, device.getVehicleId()).orElse(null);
        cache.put(device.getVehicleId(), v);
        return v;
    }

    /** Null if acceptable; otherwise why not. Devices buffer data offline, so up to 30 days old is allowed. */
    private String timeProblem(Instant t, Instant now) {
        if (t.isAfter(now.plus(MAX_FUTURE))) return "Timestamp is in the future";
        if (t.isBefore(now.minus(MAX_AGE))) return "Timestamp is more than 30 days old";
        return null;
    }

    private String stateKey(UUID vehicleId, UUID geofenceId) {
        return vehicleId + "|" + geofenceId;
    }
}