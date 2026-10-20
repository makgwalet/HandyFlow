package za.co.handyflow.platform.fleet.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.fleet.domain.model.Geofence;
import za.co.handyflow.platform.fleet.domain.model.GeofenceShape;
import za.co.handyflow.platform.fleet.domain.model.TelematicsDevice;
import za.co.handyflow.platform.fleet.domain.model.TelematicsIngestKey;
import za.co.handyflow.platform.fleet.domain.model.TelematicsSettings;
import za.co.handyflow.platform.fleet.domain.model.Vehicle;
import za.co.handyflow.platform.fleet.domain.repository.GeofenceRepository;
import za.co.handyflow.platform.fleet.domain.repository.TelematicsDeviceRepository;
import za.co.handyflow.platform.fleet.domain.repository.TelematicsIngestKeyRepository;
import za.co.handyflow.platform.fleet.domain.repository.TelematicsSettingsRepository;
import za.co.handyflow.platform.fleet.domain.repository.VehicleRepository;
import za.co.handyflow.platform.fleet.dto.CreatedIngestKeyResponse;
import za.co.handyflow.platform.fleet.dto.GeofenceResponse;
import za.co.handyflow.platform.fleet.dto.IngestKeyResponse;
import za.co.handyflow.platform.fleet.dto.SaveGeofenceRequest;
import za.co.handyflow.platform.fleet.dto.SaveGeofenceRequest.Point;
import za.co.handyflow.platform.fleet.dto.SaveTelematicsDeviceRequest;
import za.co.handyflow.platform.fleet.dto.TelematicsDeviceResponse;
import za.co.handyflow.platform.fleet.dto.TelematicsSettingsResponse;
import za.co.handyflow.platform.fleet.dto.UpdateTelematicsSettingsRequest;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Telematics set-up: ingest API keys, device registration, geofences and rule settings. */
@Slf4j
@Service
@RequiredArgsConstructor
public class TelematicsAdminService {

    private static final String KEY_PREFIX = "hf_tel_";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final TelematicsIngestKeyRepository keyRepository;
    private final TelematicsDeviceRepository deviceRepository;
    private final GeofenceRepository geofenceRepository;
    private final TelematicsSettingsRepository settingsRepository;
    private final VehicleRepository vehicleRepository;

    // ── Ingest keys ──────────────────────────────────────────────────────

    /** Generates a key, stores only its hash, and returns the plaintext once. */
    @Transactional
    public CreatedIngestKeyResponse createKey(TenantId tenantId, UUID userId, String name) {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        String apiKey = KEY_PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        TelematicsIngestKey key = TelematicsIngestKey.create(tenantId, name.trim(),
                TelematicsIngestService.hashKey(apiKey), apiKey.substring(0, 12), userId);
        keyRepository.save(key);
        log.info("Telematics ingest key created id={} tenant={}", key.getId(), tenantId);
        return new CreatedIngestKeyResponse(toKeyResponse(key), apiKey);
    }

    @Transactional(readOnly = true)
    public List<IngestKeyResponse> listKeys(TenantId tenantId) {
        return keyRepository.findAllForTenant(tenantId).stream().map(this::toKeyResponse).toList();
    }

    @Transactional
    public IngestKeyResponse revokeKey(TenantId tenantId, UUID id) {
        TelematicsIngestKey key = keyRepository.findByTenantAndId(tenantId, id)
                .orElseThrow(() -> new ResourceNotFoundException("TelematicsIngestKey", id.toString()));
        key.revoke();
        keyRepository.save(key);
        log.info("Telematics ingest key revoked id={} tenant={}", id, tenantId);
        return toKeyResponse(key);
    }

    // ── Devices ──────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<TelematicsDeviceResponse> listDevices(TenantId tenantId) {
        Map<UUID, String> regs = new HashMap<>();
        return deviceRepository.findAllForTenant(tenantId).stream()
                .map(d -> toDeviceResponse(d, registrationOf(tenantId, d.getVehicleId(), regs))).toList();
    }

    @Transactional
    public TelematicsDeviceResponse createDevice(TenantId tenantId, SaveTelematicsDeviceRequest req) {
        Vehicle vehicle = findVehicle(tenantId, req.vehicleId());
        String externalId = req.externalId().trim();
        assertExternalIdFree(tenantId, externalId, UUID.randomUUID());
        TelematicsDevice d = TelematicsDevice.create(tenantId, providerOf(req.provider()), externalId,
                vehicle.getId(), blankToNull(req.name()));
        deviceRepository.save(d);
        return toDeviceResponse(d, vehicle.getRegistration());
    }

    @Transactional
    public TelematicsDeviceResponse updateDevice(TenantId tenantId, UUID id, SaveTelematicsDeviceRequest req) {
        TelematicsDevice d = deviceRepository.findByTenantAndId(tenantId, id)
                .orElseThrow(() -> new ResourceNotFoundException("TelematicsDevice", id.toString()));
        Vehicle vehicle = findVehicle(tenantId, req.vehicleId());
        String externalId = req.externalId().trim();
        assertExternalIdFree(tenantId, externalId, id);
        d.update(providerOf(req.provider()), externalId, vehicle.getId(), blankToNull(req.name()),
                req.active() == null || req.active());
        deviceRepository.save(d);
        return toDeviceResponse(d, vehicle.getRegistration());
    }

    // ── Geofences ────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<GeofenceResponse> listGeofences(TenantId tenantId) {
        return geofenceRepository.findAllLive(tenantId).stream().map(this::toGeofenceResponse).toList();
    }

    @Transactional
    public GeofenceResponse createGeofence(TenantId tenantId, SaveGeofenceRequest req) {
        Geofence g = Geofence.create(tenantId);
        applyGeofence(g, req);
        geofenceRepository.save(g);
        return toGeofenceResponse(g);
    }

    @Transactional
    public GeofenceResponse updateGeofence(TenantId tenantId, UUID id, SaveGeofenceRequest req) {
        Geofence g = geofenceRepository.findLiveById(tenantId, id)
                .orElseThrow(() -> new ResourceNotFoundException("Geofence", id.toString()));
        applyGeofence(g, req);
        geofenceRepository.save(g);
        return toGeofenceResponse(g);
    }

    @Transactional
    public void deleteGeofence(TenantId tenantId, UUID id) {
        Geofence g = geofenceRepository.findLiveById(tenantId, id)
                .orElseThrow(() -> new ResourceNotFoundException("Geofence", id.toString()));
        g.softDelete();
        geofenceRepository.save(g);
    }

    private void applyGeofence(Geofence g, SaveGeofenceRequest req) {
        String polygonJson = null;
        if (req.shape() == GeofenceShape.POLYGON) {
            List<Point> pts = req.polygon();
            if (pts == null || pts.size() < 3) {
                throw new IllegalArgumentException("A polygon needs at least three points");
            }
            double[][] arr = pts.stream().map(p -> new double[]{p.latitude(), p.longitude()}).toArray(double[][]::new);
            polygonJson = GeoMath.toPolygonJson(arr);
        }
        g.apply(req.name().trim(), req.shape(), req.centerLatitude(), req.centerLongitude(), req.radiusMeters(),
                polygonJson, req.alertOnEnter(), req.alertOnExit(), req.speedLimitKph(),
                req.active() == null || req.active());
    }

    // ── Settings ─────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public TelematicsSettingsResponse getSettings(TenantId tenantId) {
        return settingsRepository.findByTenantId(tenantId)
                .map(s -> toSettingsResponse(s, false))
                .orElseGet(() -> toSettingsResponse(TelematicsSettings.defaults(tenantId), true));
    }

    @Transactional
    public TelematicsSettingsResponse updateSettings(TenantId tenantId, UUID userId, UpdateTelematicsSettingsRequest req) {
        TelematicsSettings s = settingsRepository.findByTenantId(tenantId)
                .orElseGet(() -> TelematicsSettings.defaults(tenantId));
        s.apply(req.defaultSpeedLimitKph(), req.speedingToleranceKph(), req.speedingCooldownMinutes(),
                req.alertOverspeedKph(), req.idlingThresholdMinutes(), req.offlineAfterMinutes(), userId);
        settingsRepository.save(s);
        return toSettingsResponse(s, false);
    }

    // ── Helpers & mapping ────────────────────────────────────────────────

    private Vehicle findVehicle(TenantId tenantId, UUID id) {
        return vehicleRepository.findActiveById(tenantId, id)
                .orElseThrow(() -> new ResourceNotFoundException("Vehicle", id.toString()));
    }

    private void assertExternalIdFree(TenantId tenantId, String externalId, UUID excludeId) {
        if (deviceRepository.existsOtherWithExternalId(tenantId, externalId, excludeId)) {
            throw new IllegalArgumentException("A device with id " + externalId + " is already registered");
        }
    }

    private String registrationOf(TenantId tenantId, UUID vehicleId, Map<UUID, String> cache) {
        return cache.computeIfAbsent(vehicleId, id -> vehicleRepository.findActiveById(tenantId, id)
                .map(Vehicle::getRegistration).orElse(null));
    }

    private String providerOf(String raw) {
        String p = blankToNull(raw);
        return p == null ? "GENERIC" : p.toUpperCase();
    }

    private String blankToNull(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    private IngestKeyResponse toKeyResponse(TelematicsIngestKey k) {
        return new IngestKeyResponse(k.getId(), k.getName(), k.getKeyPrefix(), k.getCreatedAt(), k.getLastUsedAt(),
                k.getRevokedAt(), k.isActive());
    }

    private TelematicsDeviceResponse toDeviceResponse(TelematicsDevice d, String registration) {
        return new TelematicsDeviceResponse(d.getId(), d.getProvider(), d.getExternalId(), d.getVehicleId(),
                registration, d.getName(), d.isActive(), d.getLastSeenAt());
    }

    private GeofenceResponse toGeofenceResponse(Geofence g) {
        List<Point> polygon = null;
        if (g.getShape() == GeofenceShape.POLYGON) {
            double[][] arr = GeoMath.parsePolygon(g.getPolygonJson());
            polygon = Arrays.stream(arr).map(p -> new Point(p[0], p[1])).toList();
        }
        return new GeofenceResponse(g.getId(), g.getName(), g.getShape().name(), g.getCenterLatitude(),
                g.getCenterLongitude(), g.getRadiusMeters(), polygon, g.isAlertOnEnter(), g.isAlertOnExit(),
                g.getSpeedLimitKph(), g.isActive());
    }

    private TelematicsSettingsResponse toSettingsResponse(TelematicsSettings s, boolean usingDefaults) {
        return new TelematicsSettingsResponse(s.getDefaultSpeedLimitKph(), s.getSpeedingToleranceKph(),
                s.getSpeedingCooldownMinutes(), s.getAlertOverspeedKph(), s.getIdlingThresholdMinutes(),
                s.getOfflineAfterMinutes(), usingDefaults, usingDefaults ? null : s.getUpdatedAt());
    }
}