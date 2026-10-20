package za.co.handyflow.platform.fleet.application.internal;

import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.fleet.domain.model.Driver;
import za.co.handyflow.platform.fleet.domain.model.Geofence;
import za.co.handyflow.platform.fleet.domain.model.TelematicsEvent;
import za.co.handyflow.platform.fleet.domain.model.TelematicsEventType;
import za.co.handyflow.platform.fleet.domain.model.TelematicsPosition;
import za.co.handyflow.platform.fleet.domain.model.TelematicsSettings;
import za.co.handyflow.platform.fleet.domain.model.Vehicle;
import za.co.handyflow.platform.fleet.domain.model.VehicleLastPosition;
import za.co.handyflow.platform.fleet.domain.repository.DriverRepository;
import za.co.handyflow.platform.fleet.domain.repository.GeofenceRepository;
import za.co.handyflow.platform.fleet.domain.repository.TelematicsEventRepository;
import za.co.handyflow.platform.fleet.domain.repository.TelematicsPositionRepository;
import za.co.handyflow.platform.fleet.domain.repository.TelematicsSettingsRepository;
import za.co.handyflow.platform.fleet.domain.repository.VehicleLastPositionRepository;
import za.co.handyflow.platform.fleet.domain.repository.VehicleRepository;
import za.co.handyflow.platform.fleet.dto.LivePositionResponse;
import za.co.handyflow.platform.fleet.dto.TelematicsEventResponse;
import za.co.handyflow.platform.fleet.dto.TrackPointResponse;
import za.co.handyflow.platform.fleet.dto.VehicleTrackResponse;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Read side of telematics: the live map, a vehicle's track, and the event list. */
@Service
@RequiredArgsConstructor
public class TelematicsQueryService {

    private static final int DEFAULT_MAX_POINTS = 1000;
    private static final int HARD_MAX_POINTS = 5000;
    private static final Duration MAX_TRACK_RANGE = Duration.ofDays(7);

    private final VehicleRepository vehicleRepository;
    private final VehicleLastPositionRepository lastPositionRepository;
    private final TelematicsPositionRepository positionRepository;
    private final TelematicsEventRepository eventRepository;
    private final TelematicsSettingsRepository settingsRepository;
    private final GeofenceRepository geofenceRepository;
    private final DriverRepository driverRepository;

    // ── Live map ─────────────────────────────────────────────────────────

    /** Every tracked vehicle's last known position, most recently heard from first. */
    @Transactional(readOnly = true)
    public List<LivePositionResponse> live(TenantId tenantId) {
        TelematicsSettings settings = settingsRepository.findByTenantId(tenantId)
                .orElseGet(() -> TelematicsSettings.defaults(tenantId));
        Instant now = Instant.now();
        Map<UUID, Vehicle> cache = new HashMap<>();

        List<LivePositionResponse> out = new ArrayList<>();
        for (VehicleLastPosition p : lastPositionRepository.findAllForTenant(tenantId)) {
            Vehicle v = cache.computeIfAbsent(p.getVehicleId(),
                    id -> vehicleRepository.findActiveById(tenantId, id).orElse(null));
            if (v == null) continue;
            long age = Math.max(0, Duration.between(p.getRecordedAt(), now).toMinutes());
            out.add(new LivePositionResponse(v.getId(), v.getRegistration(), v.getMake(), v.getModel(),
                    p.getLatitude(), p.getLongitude(), p.getSpeedKph(), p.getHeading(), p.getIgnitionOn(),
                    p.getRecordedAt(), age, statusOf(p, age, settings.getOfflineAfterMinutes())));
        }
        return out;
    }

    /** OFFLINE if not heard from recently; else from speed and ignition. Without an ignition feed, stationary is just STOPPED. */
    private String statusOf(VehicleLastPosition p, long ageMinutes, int offlineAfterMinutes) {
        if (ageMinutes > offlineAfterMinutes) return "OFFLINE";
        boolean moving = p.getSpeedKph() != null && p.getSpeedKph() >= 2.0;
        if (moving) return "MOVING";
        if (Boolean.TRUE.equals(p.getIgnitionOn())) return "IDLE";
        if (Boolean.FALSE.equals(p.getIgnitionOn())) return "PARKED";
        return "STOPPED";
    }

    // ── Track ────────────────────────────────────────────────────────────

    /**
     * A vehicle's GPS track over a range of at most 7 days (default: the last 24 hours), thinned to
     * {@code maxPoints} by taking evenly spaced fixes, always keeping the first and the last.
     * The distance is the straight-line sum over <i>all</i> fixes — a GPS estimate, not the odometer.
     */
    @Transactional(readOnly = true)
    public VehicleTrackResponse track(TenantId tenantId, UUID vehicleId, Instant from, Instant to, Integer maxPoints) {
        Vehicle vehicle = vehicleRepository.findActiveById(tenantId, vehicleId)
                .orElseThrow(() -> new ResourceNotFoundException("Vehicle", vehicleId.toString()));
        Instant end = to != null ? to : Instant.now();
        Instant start = from != null ? from : end.minus(Duration.ofHours(24));
        if (start.isAfter(end)) throw new IllegalArgumentException("'from' cannot be after 'to'");
        if (Duration.between(start, end).compareTo(MAX_TRACK_RANGE) > 0) {
            throw new IllegalArgumentException("A track can cover at most 7 days");
        }
        int cap = Math.max(2, Math.min(maxPoints != null ? maxPoints : DEFAULT_MAX_POINTS, HARD_MAX_POINTS));

        List<TelematicsPosition> all = positionRepository.findTrack(vehicleId, start, end);
        double meters = 0;
        for (int i = 1; i < all.size(); i++) {
            TelematicsPosition a = all.get(i - 1), b = all.get(i);
            meters += GeoMath.haversineMeters(a.getLatitude(), a.getLongitude(), b.getLatitude(), b.getLongitude());
        }

        List<TelematicsPosition> chosen = all;
        if (all.size() > cap) {
            chosen = new ArrayList<>(cap);
            double step = (all.size() - 1) / (double) (cap - 1);
            for (int i = 0; i < cap; i++) chosen.add(all.get((int) Math.round(i * step)));
        }
        List<TrackPointResponse> points = chosen.stream()
                .map(p -> new TrackPointResponse(p.getRecordedAt(), p.getLatitude(), p.getLongitude(),
                        p.getSpeedKph(), p.getHeading(), p.getIgnitionOn())).toList();
        return new VehicleTrackResponse(vehicleId, vehicle.getRegistration(), start, end, all.size(),
                points.size(), Math.round(meters / 100.0) / 10.0, points);
    }

    // ── Events ───────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Page<TelematicsEventResponse> events(TenantId tenantId, UUID vehicleId, UUID driverId,
                                                TelematicsEventType type, Instant from, Instant to,
                                                boolean unacknowledgedOnly, Pageable pageable) {
        Specification<TelematicsEvent> spec = (root, query, cb) -> {
            List<Predicate> p = new ArrayList<>();
            p.add(cb.equal(root.get("tenantId").get("value"), tenantId.getValue()));
            if (vehicleId != null) p.add(cb.equal(root.get("vehicleId"), vehicleId));
            if (driverId != null) p.add(cb.equal(root.get("driverId"), driverId));
            if (type != null) p.add(cb.equal(root.get("type"), type));
            if (from != null) p.add(cb.greaterThanOrEqualTo(root.get("occurredAt"), from));
            if (to != null) p.add(cb.lessThan(root.get("occurredAt"), to));
            if (unacknowledgedOnly) p.add(cb.isNull(root.get("acknowledgedAt")));
            return cb.and(p.toArray(new Predicate[0]));
        };

        Map<UUID, String> regs = new HashMap<>();
        Map<UUID, String> drivers = new HashMap<>();
        Map<UUID, String> fences = new HashMap<>();
        return eventRepository.findAll(spec, pageable).map(e -> toResponse(e,
                regs.computeIfAbsent(e.getVehicleId(), id -> vehicleRepository.findActiveById(tenantId, id)
                        .map(Vehicle::getRegistration).orElse(null)),
                e.getDriverId() == null ? null : drivers.computeIfAbsent(e.getDriverId(),
                        id -> driverRepository.findActiveById(tenantId, id).map(Driver::getFullName).orElse(null)),
                e.getGeofenceId() == null ? null : fences.computeIfAbsent(e.getGeofenceId(),
                        id -> geofenceRepository.findById(id).map(Geofence::getName).orElse(null))));
    }

    @Transactional
    public TelematicsEventResponse acknowledge(TenantId tenantId, UUID eventId, UUID userId) {
        TelematicsEvent e = eventRepository.findByTenantAndId(tenantId, eventId)
                .orElseThrow(() -> new ResourceNotFoundException("TelematicsEvent", eventId.toString()));
        e.acknowledge(userId);
        eventRepository.save(e);
        return toResponse(e,
                vehicleRepository.findActiveById(tenantId, e.getVehicleId()).map(Vehicle::getRegistration).orElse(null),
                e.getDriverId() == null ? null
                        : driverRepository.findActiveById(tenantId, e.getDriverId()).map(Driver::getFullName).orElse(null),
                e.getGeofenceId() == null ? null
                        : geofenceRepository.findById(e.getGeofenceId()).map(Geofence::getName).orElse(null));
    }

    private TelematicsEventResponse toResponse(TelematicsEvent e, String registration, String driverName, String geofenceName) {
        return new TelematicsEventResponse(e.getId(), e.getVehicleId(), registration, e.getDriverId(), driverName,
                e.getType().name(), e.getSeverity().name(), e.getOccurredAt(), e.getLatitude(), e.getLongitude(),
                e.getValue(), e.getLimitValue(), e.getDurationSeconds(), e.getDetails(), e.getGeofenceId(),
                geofenceName, e.getSource().name(), e.getAcknowledgedAt() != null, e.getAcknowledgedAt());
    }
}