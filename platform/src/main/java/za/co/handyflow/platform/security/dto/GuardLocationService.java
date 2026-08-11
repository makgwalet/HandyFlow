// security/application/internal/GuardLocationService.java

package za.co.handyflow.platform.security.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.security.domain.model.DeviceSession;
import za.co.handyflow.platform.security.domain.model.GuardLocationPing;
import za.co.handyflow.platform.security.domain.model.SecurityDevice;
import za.co.handyflow.platform.security.domain.repository.DeviceSessionRepository;
import za.co.handyflow.platform.security.domain.repository.GuardLocationPingRepository;
import za.co.handyflow.platform.security.domain.repository.SecurityDeviceRepository;
import za.co.handyflow.platform.security.dto.RecordLocationPingRequest;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.util.UUID;

/**
 * GuardLocationService — GPS ping ingestion (backend-only pass; no
 * "current locations for the map" read endpoint yet -- that's the next
 * stage of the real-GPS-map feature).
 *
 * Every ping does two things in one transaction:
 *   1. Append to security_guard_location_pings (history, via
 *      GuardLocationPingRepository -- normal JPA save).
 *   2. Upsert security_guard_current_location (single row per guard, via
 *      raw JDBC INSERT ... ON CONFLICT). No JPA entity for this table --
 *      it's purely a "last write wins" projection with no independent
 *      identity/lifecycle of its own, so mapping it as an entity (with
 *      Hibernate versioning/dirty-checking machinery it doesn't need)
 *      would be overhead for no benefit. Same "drop to JdbcTemplate for
 *      imperative writes the ORM doesn't need to own" convention already
 *      established elsewhere in this module (ClientPortalService,
 *      IncidentService's type-column update, GuardService's employee-code
 *      sequence claim).
 *
 * WHY resolve siteId from the device, not the shift?
 * A DeviceSession can be open with no linked Shift (spot-check case), but
 * the device itself always belongs to a site (SecurityDevice.siteId) --
 * that's the more reliable source for "which site is this guard pinging
 * from" regardless of whether a shift happens to be attached.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GuardLocationService {

    /**
     * Matches the guard app's stated ping interval (see LiveMapTab.tsx's own
     * "Coming soon" copy: "GPS pings every 5 minutes"). Not yet consumed by
     * anything in this pass -- the future "current locations for map" query
     * will filter security_guard_current_location.recorded_at against this
     * threshold to decide whether a guard is shown as live or stale/offline.
     * Defined here now so the read-side implementation doesn't have to
     * rediscover/re-decide this number later.
     */
    public static final int LIVENESS_THRESHOLD_MINUTES = 5;

    private final DeviceSessionRepository     sessionRepository;
    private final SecurityDeviceRepository    deviceRepository;
    private final GuardLocationPingRepository pingRepository;
    private final JdbcTemplate                jdbc;

    @Transactional
    public void recordPing(TenantId tenantId, UUID sessionId, RecordLocationPingRequest req) {
        DeviceSession session = sessionRepository.findById(sessionId)
                .filter(s -> s.getTenantId().equals(tenantId) && s.isOpen())
                .orElseThrow(() -> new HandyFlowException(
                        "No open session found for this ID — cannot record a location ping "
                                + "against a closed or nonexistent session",
                        HttpStatus.BAD_REQUEST, "SESSION_NOT_OPEN"));

        UUID guardId = session.getGuardId();
        UUID shiftId = session.getShiftId();
        UUID siteId  = deviceRepository.findById(session.getDeviceId())
                .map(SecurityDevice::getSiteId)
                .orElse(null);

        Instant now = Instant.now();

        // 1. Append to history
        GuardLocationPing ping = GuardLocationPing.record(
                tenantId, guardId, shiftId, sessionId,
                req.latitude(), req.longitude(), req.accuracyMetres());
        pingRepository.save(ping);

        // 2. Upsert current position
        jdbc.update("""
                INSERT INTO security_guard_current_location
                    (guard_id, tenant_id, shift_id, site_id, latitude, longitude, recorded_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (guard_id) DO UPDATE SET
                    tenant_id   = EXCLUDED.tenant_id,
                    shift_id    = EXCLUDED.shift_id,
                    site_id     = EXCLUDED.site_id,
                    latitude    = EXCLUDED.latitude,
                    longitude   = EXCLUDED.longitude,
                    recorded_at = EXCLUDED.recorded_at,
                    updated_at  = EXCLUDED.updated_at
                """,
                guardId, tenantId.getValue(), shiftId, siteId,
                req.latitude(), req.longitude(), java.sql.Timestamp.from(now), java.sql.Timestamp.from(now));

        log.debug("[Security] Location ping recorded guardId={} sessionId={} siteId={}",
                guardId, sessionId, siteId);
    }
}