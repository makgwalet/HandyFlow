// security/domain/model/GuardLocationPing.java

package za.co.handyflow.platform.security.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * GuardLocationPing — one GPS position report from the guard app, recorded
 * roughly every 5 minutes while a DeviceSession is open. Append-only, same
 * convention as ArmouryLog/CheckpointLog elsewhere in this module.
 *
 * NOT read by the live map -- see security_guard_current_location (no JPA
 * entity, managed via raw upsert in GuardLocationService) for that. This
 * table is the audit trail / future input to patrol-pattern anomaly
 * detection (the audit doc's own "New technology opportunities" item).
 *
 * WHY tied to deviceSessionId, not just shiftId?
 * A DeviceSession can exist without a linked Shift (the spot-check case --
 * see DeviceSessionService.openSession()'s own comment: "If no matching
 * shift: session is allowed but no shift is started"). Session is the
 * established identity anchor for guard actions throughout this module
 * (checkpoint scans resolve guardId from the session, never a client
 * claim); location pings follow the same posture.
 */
@Entity
@Table(name = "security_guard_location_pings")
@Getter
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class GuardLocationPing {

    @Id
    private UUID id = UUID.randomUUID();

    @Embedded
    @AttributeOverride(name = "value",
            column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;

    @Column(name = "guard_id", nullable = false)
    private UUID guardId;

    @Column(name = "shift_id")
    private UUID shiftId;

    @Column(name = "device_session_id", nullable = false)
    private UUID deviceSessionId;

    @Column(nullable = false, precision = 10, scale = 7)
    private BigDecimal latitude;

    @Column(nullable = false, precision = 10, scale = 7)
    private BigDecimal longitude;

    @Column(name = "accuracy_metres", precision = 8, scale = 2)
    private BigDecimal accuracyMetres;

    @Column(name = "recorded_at", nullable = false)
    private Instant recordedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    // ── Factory ────────────────────────────────────────────────────────────────

    public static GuardLocationPing record(TenantId tenantId, UUID guardId, UUID shiftId,
                                           UUID deviceSessionId, BigDecimal latitude,
                                           BigDecimal longitude, BigDecimal accuracyMetres) {
        GuardLocationPing p   = new GuardLocationPing();
        p.tenantId            = tenantId;
        p.guardId             = guardId;
        p.shiftId             = shiftId;
        p.deviceSessionId     = deviceSessionId;
        p.latitude            = latitude;
        p.longitude           = longitude;
        p.accuracyMetres      = accuracyMetres;
        p.recordedAt          = Instant.now();
        p.createdAt           = Instant.now();
        return p;
    }
}