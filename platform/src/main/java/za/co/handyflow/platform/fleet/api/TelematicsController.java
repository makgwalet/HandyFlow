package za.co.handyflow.platform.fleet.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.billing.FeatureGuard;
import za.co.handyflow.platform.fleet.application.internal.TelematicsAdminService;
import za.co.handyflow.platform.fleet.application.internal.TelematicsQueryService;
import za.co.handyflow.platform.fleet.domain.model.TelematicsEventType;
import za.co.handyflow.platform.fleet.dto.*;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;
import za.co.handyflow.platform.shared.UserContext;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Telematics for users: live map, tracks, events, and set-up (devices, geofences, settings, ingest keys).
 * FLEET_READ to view; FLEET_MANAGE for devices, geofences and acknowledging events; FLEET_ADMIN for ingest
 * keys (they are credentials) and for the rule settings that decide what counts as speeding.
 */
@RestController
@RequestMapping("/api/v1/fleet")
@RequiredArgsConstructor
@Tag(name = "Fleet - Telematics", description = "Live positions, tracks, driving events, geofences and set-up")
public class TelematicsController {

    private final TelematicsQueryService queryService;
    private final TelematicsAdminService adminService;
    private final FeatureGuard featureGuard;

    // ── Live & history ────────────────────────────────────────────────────────

    @GetMapping("/telematics/live")
    @PreAuthorize("hasAuthority('FLEET_READ')")
    @Operation(summary = "Last known position of every tracked vehicle, with status MOVING / IDLE / STOPPED / PARKED / OFFLINE")
    public ResponseEntity<ApiResponse<List<LivePositionResponse>>> live() {
        featureGuard.requireModule("fleet");
        return ResponseEntity.ok(ApiResponse.success(queryService.live(TenantContext.getTenantIdAsObject())));
    }

    @GetMapping("/vehicles/{vehicleId}/telematics/track")
    @PreAuthorize("hasAuthority('FLEET_READ')")
    @Operation(summary = "A vehicle's GPS track (default last 24 h, max 7 days), thinned to maxPoints (default 1,000, max 5,000)")
    public ResponseEntity<ApiResponse<VehicleTrackResponse>> track(
            @PathVariable UUID vehicleId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(required = false) Integer maxPoints) {
        featureGuard.requireModule("fleet");
        return ResponseEntity.ok(ApiResponse.success(
                queryService.track(TenantContext.getTenantIdAsObject(), vehicleId, from, to, maxPoints)));
    }

    @GetMapping("/telematics/events")
    @PreAuthorize("hasAuthority('FLEET_READ')")
    @Operation(summary = "Driving events (speeding, harsh braking, idling, geofence crossings, ...), newest first; "
            + "filter by vehicle, driver, type, time range, or unacknowledged only")
    public ResponseEntity<ApiResponse<Page<TelematicsEventResponse>>> events(
            @RequestParam(required = false) UUID vehicleId,
            @RequestParam(required = false) UUID driverId,
            @RequestParam(required = false) TelematicsEventType type,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(defaultValue = "false") boolean unacknowledgedOnly,
            @PageableDefault(size = 50, sort = "occurredAt", direction = Sort.Direction.DESC) Pageable pageable) {
        featureGuard.requireModule("fleet");
        return ResponseEntity.ok(ApiResponse.success(queryService.events(TenantContext.getTenantIdAsObject(),
                vehicleId, driverId, type, from, to, unacknowledgedOnly, pageable)));
    }

    @PostMapping("/telematics/events/{id}/acknowledge")
    @PreAuthorize("hasAuthority('FLEET_MANAGE')")
    public ResponseEntity<ApiResponse<TelematicsEventResponse>> acknowledge(@PathVariable UUID id) {
        featureGuard.requireModule("fleet");
        return ResponseEntity.ok(ApiResponse.success("Event acknowledged",
                queryService.acknowledge(TenantContext.getTenantIdAsObject(), id, UserContext.getCurrentUserId())));
    }

    // ── Devices ───────────────────────────────────────────────────────────────

    @GetMapping("/telematics/devices")
    @PreAuthorize("hasAuthority('FLEET_READ')")
    public ResponseEntity<ApiResponse<List<TelematicsDeviceResponse>>> devices() {
        featureGuard.requireModule("fleet");
        return ResponseEntity.ok(ApiResponse.success(adminService.listDevices(TenantContext.getTenantIdAsObject())));
    }

    @PostMapping("/telematics/devices")
    @PreAuthorize("hasAuthority('FLEET_MANAGE')")
    @Operation(summary = "Register a provider's unit id against a vehicle. Data for unregistered ids is rejected at ingest")
    public ResponseEntity<ApiResponse<TelematicsDeviceResponse>> createDevice(
            @Valid @RequestBody SaveTelematicsDeviceRequest request) {
        featureGuard.requireModule("fleet");
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Device registered",
                adminService.createDevice(TenantContext.getTenantIdAsObject(), request)));
    }

    @PutMapping("/telematics/devices/{id}")
    @PreAuthorize("hasAuthority('FLEET_MANAGE')")
    @Operation(summary = "Move a device to another vehicle, rename it, or deactivate it (there is no delete)")
    public ResponseEntity<ApiResponse<TelematicsDeviceResponse>> updateDevice(
            @PathVariable UUID id, @Valid @RequestBody SaveTelematicsDeviceRequest request) {
        featureGuard.requireModule("fleet");
        return ResponseEntity.ok(ApiResponse.success("Device updated",
                adminService.updateDevice(TenantContext.getTenantIdAsObject(), id, request)));
    }

    // ── Geofences ─────────────────────────────────────────────────────────────

    @GetMapping("/telematics/geofences")
    @PreAuthorize("hasAuthority('FLEET_READ')")
    public ResponseEntity<ApiResponse<List<GeofenceResponse>>> geofences() {
        featureGuard.requireModule("fleet");
        return ResponseEntity.ok(ApiResponse.success(adminService.listGeofences(TenantContext.getTenantIdAsObject())));
    }

    @PostMapping("/telematics/geofences")
    @PreAuthorize("hasAuthority('FLEET_MANAGE')")
    @Operation(summary = "Create a circular or polygon geofence; optionally alert on enter / exit and impose a lower speed limit inside it")
    public ResponseEntity<ApiResponse<GeofenceResponse>> createGeofence(@Valid @RequestBody SaveGeofenceRequest request) {
        featureGuard.requireModule("fleet");
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Geofence created",
                adminService.createGeofence(TenantContext.getTenantIdAsObject(), request)));
    }

    @PutMapping("/telematics/geofences/{id}")
    @PreAuthorize("hasAuthority('FLEET_MANAGE')")
    public ResponseEntity<ApiResponse<GeofenceResponse>> updateGeofence(
            @PathVariable UUID id, @Valid @RequestBody SaveGeofenceRequest request) {
        featureGuard.requireModule("fleet");
        return ResponseEntity.ok(ApiResponse.success("Geofence updated",
                adminService.updateGeofence(TenantContext.getTenantIdAsObject(), id, request)));
    }

    @DeleteMapping("/telematics/geofences/{id}")
    @PreAuthorize("hasAuthority('FLEET_MANAGE')")
    public ResponseEntity<ApiResponse<Void>> deleteGeofence(@PathVariable UUID id) {
        featureGuard.requireModule("fleet");
        adminService.deleteGeofence(TenantContext.getTenantIdAsObject(), id);
        return ResponseEntity.ok(ApiResponse.success("Geofence deleted", null));
    }

    // ── Settings ──────────────────────────────────────────────────────────────

    @GetMapping("/telematics/settings")
    @PreAuthorize("hasAuthority('FLEET_READ')")
    public ResponseEntity<ApiResponse<TelematicsSettingsResponse>> settings() {
        featureGuard.requireModule("fleet");
        return ResponseEntity.ok(ApiResponse.success(adminService.getSettings(TenantContext.getTenantIdAsObject())));
    }

    @PutMapping("/telematics/settings")
    @PreAuthorize("hasAuthority('FLEET_ADMIN')")
    @Operation(summary = "Speed limit, tolerance, cooldown, idling and offline thresholds. The 120 km/h default is the highest "
            + "legal limit — lower it, or draw slower geofences, or town speeding goes unnoticed")
    public ResponseEntity<ApiResponse<TelematicsSettingsResponse>> updateSettings(
            @Valid @RequestBody UpdateTelematicsSettingsRequest request) {
        featureGuard.requireModule("fleet");
        return ResponseEntity.ok(ApiResponse.success("Telematics settings saved",
                adminService.updateSettings(TenantContext.getTenantIdAsObject(), UserContext.getCurrentUserId(), request)));
    }

    // ── Ingest keys ───────────────────────────────────────────────────────────

    @GetMapping("/telematics/ingest-keys")
    @PreAuthorize("hasAuthority('FLEET_ADMIN')")
    public ResponseEntity<ApiResponse<List<IngestKeyResponse>>> keys() {
        featureGuard.requireModule("fleet");
        return ResponseEntity.ok(ApiResponse.success(adminService.listKeys(TenantContext.getTenantIdAsObject())));
    }

    @PostMapping("/telematics/ingest-keys")
    @PreAuthorize("hasAuthority('FLEET_ADMIN')")
    @Operation(summary = "Create an API key for a provider. The key is shown ONCE in this response and cannot be retrieved later")
    public ResponseEntity<ApiResponse<CreatedIngestKeyResponse>> createKey(@Valid @RequestBody CreateIngestKeyRequest request) {
        featureGuard.requireModule("fleet");
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("API key created — copy it now",
                adminService.createKey(TenantContext.getTenantIdAsObject(), UserContext.getCurrentUserId(), request.name())));
    }

    @DeleteMapping("/telematics/ingest-keys/{id}")
    @PreAuthorize("hasAuthority('FLEET_ADMIN')")
    @Operation(summary = "Revoke an API key immediately (the provider's next request gets a 401)")
    public ResponseEntity<ApiResponse<IngestKeyResponse>> revokeKey(@PathVariable UUID id) {
        featureGuard.requireModule("fleet");
        return ResponseEntity.ok(ApiResponse.success("API key revoked",
                adminService.revokeKey(TenantContext.getTenantIdAsObject(), id)));
    }
}