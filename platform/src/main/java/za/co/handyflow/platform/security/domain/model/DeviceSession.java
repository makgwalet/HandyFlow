// security/domain/model/DeviceSession.java

package za.co.handyflow.platform.security.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * DeviceSession — a guard's authenticated session on a specific device.
 *
 * This is the identity anchor for all Phase 2 guard actions.  Every checkpoint
 * scan, incident report, and resource checkout is attributed to the guard whose
 * session is currently open on the device — server-side, not from a client claim.
 *
 * This closes loophole #13 permanently: the server resolves guardId from the
 * open session, never trusting a client-supplied guardId field.
 *
 * Lifecycle (Part 6.3):
 *   1. Guard authenticates (PIN + face liveness on-device)
 *   2. Server verifies PIN, opens this session row
 *   3. All subsequent scans/incidents read guard_id from this row
 *   4. Guard clocks out → ended_at set, session closed
 *   5. Kiosk returns to lock screen
 *
 * Only one open session per device (UNIQUE NULLS NOT DISTINCT on device_id + ended_at).
 * Only one open session per guard (partial unique index on guard_id WHERE ended_at IS NULL).
 *
 * WHY store face match confidence here and not on the guard record?
 * Face match confidence is session-specific evidence — it proves that THIS guard,
 * on THIS device, at THIS moment passed the liveness check.  Storing it on the
 * guard record would overwrite the historical evidence of previous sessions.
 */
@Entity
@Table(name = "security_device_sessions")
@Getter
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class DeviceSession {

    @Id
    private UUID id = UUID.randomUUID();

    @Embedded
    @AttributeOverride(name = "value",
            column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;

    @Column(name = "device_id", nullable = false)
    private UUID deviceId;

    @Column(name = "guard_id", nullable = false)
    private UUID guardId;

    @Column(name = "shift_id")
    private UUID shiftId;

    @Column(name = "started_at", nullable = false, updatable = false)
    private Instant startedAt;

    @Column(name = "ended_at")
    private Instant endedAt;

    @Column(name = "start_pin_verified", nullable = false)
    private boolean startPinVerified;

    @Column(name = "start_face_match_confidence", precision = 5, scale = 4)
    private BigDecimal startFaceMatchConfidence;

    @Column(name = "start_geofence_ok")
    private Boolean startGeofenceOk;

    @Column(name = "end_pin_verified")
    private Boolean endPinVerified;

    @Column(name = "end_face_match_confidence", precision = 5, scale = 4)
    private BigDecimal endFaceMatchConfidence;

    @Column(name = "handover_notes")
    private String handoverNotes;

    @Column(name = "forced_close_reason", length = 200)
    private String forcedCloseReason;

    @Column(name = "forced_close_by")
    private UUID forcedCloseBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    // ── Factory ────────────────────────────────────────────────────────────────

    public static DeviceSession open(TenantId tenantId, UUID deviceId, UUID guardId,
                                     UUID shiftId, boolean pinVerified,
                                     BigDecimal faceConfidence, Boolean geofenceOk) {
        DeviceSession s          = new DeviceSession();
        s.tenantId               = tenantId;
        s.deviceId               = deviceId;
        s.guardId                = guardId;
        s.shiftId                = shiftId;
        s.startedAt              = Instant.now();
        s.startPinVerified       = pinVerified;
        s.startFaceMatchConfidence = faceConfidence;
        s.startGeofenceOk        = geofenceOk;
        s.createdAt              = Instant.now();
        return s;
    }

    // ── State transitions ──────────────────────────────────────────────────────

    /**
     * Normal clock-out — guard ends their shift on the device.
     */
    public void close(boolean endPinVerified, BigDecimal endFaceConfidence,
                      String handoverNotes) {
        if (this.endedAt != null) {
            throw new IllegalStateException("Session already closed");
        }
        this.endedAt                = Instant.now();
        this.endPinVerified         = endPinVerified;
        this.endFaceMatchConfidence = endFaceConfidence;
        this.handoverNotes          = handoverNotes;
    }

    /**
     * Supervisor force-close — e.g. previous guard forgot to clock out.
     * Logged separately from a normal close so the audit trail shows why
     * the session was ended without the guard's own clock-out.
     */
    public void forceClose(UUID supervisorId, String reason) {
        if (this.endedAt != null) {
            throw new IllegalStateException("Session already closed");
        }
        this.endedAt           = Instant.now();
        this.forcedCloseBy     = supervisorId;
        this.forcedCloseReason = reason;
    }

    // ── Queries ────────────────────────────────────────────────────────────────

    public boolean isOpen()   { return endedAt == null; }
    public boolean isClosed() { return endedAt != null; }

    /** Duration in minutes — null if session is still open. */
    public Long durationMinutes() {
        if (endedAt == null) return null;
        return java.time.Duration.between(startedAt, endedAt).toMinutes();
    }
}