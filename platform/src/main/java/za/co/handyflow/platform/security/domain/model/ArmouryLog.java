// security/domain/model/ArmouryLog.java

package za.co.handyflow.platform.security.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.util.UUID;

/**
 * ArmouryLog — an immutable record of one firearm issue or return event.
 *
 * Two-person witness is mandatory (witnessedByGuardId is NOT NULL) — this is
 * the Firearms Control Act compliance requirement that distinguishes this
 * from the generic, optionally-witnessed security_resource_custody checkout.
 *
 * WHY a separate, append-only log rather than just updating Armoury's
 * assignedGuardId/status?
 * The Armoury entity tracks current state; this log is the permanent,
 * never-edited audit trail a company must produce if a firearm's chain of
 * custody is ever challenged — every issue and return, who witnessed it,
 * and when, going back to the firearm's registration.
 */
@Entity
@Table(name = "security_armoury_logs")
@Getter
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class ArmouryLog {

    @Id
    private UUID id = UUID.randomUUID();

    @Embedded
    @AttributeOverride(name = "value",
            column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;

    @Column(name = "armoury_id", nullable = false)
    private UUID armouryId;

    @Column(name = "guard_id", nullable = false)
    private UUID guardId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private ArmouryAction action;

    @Column(name = "witnessed_by_guard_id", nullable = false)
    private UUID witnessedByGuardId;

    @Column(name = "session_id")
    private UUID sessionId;

    @Column(name = "shift_id")
    private UUID shiftId;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;

    @Column(name = "condition_notes")
    private String conditionNotes;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    // ── Factory ────────────────────────────────────────────────────────────────

    public static ArmouryLog record(TenantId tenantId, UUID armouryId, UUID guardId,
                                    ArmouryAction action, UUID witnessedByGuardId,
                                    UUID sessionId, UUID shiftId, String conditionNotes) {
        if (witnessedByGuardId == null) {
            throw new IllegalArgumentException(
                    "Firearm " + action + " requires a witness — Firearms Control Act compliance");
        }
        if (witnessedByGuardId.equals(guardId)) {
            throw new IllegalArgumentException(
                    "The witness must be a different guard from the one issuing/returning");
        }
        ArmouryLog log         = new ArmouryLog();
        log.tenantId           = tenantId;
        log.armouryId          = armouryId;
        log.guardId            = guardId;
        log.action             = action;
        log.witnessedByGuardId = witnessedByGuardId;
        log.sessionId          = sessionId;
        log.shiftId            = shiftId;
        log.conditionNotes     = conditionNotes;
        log.occurredAt         = Instant.now();
        log.createdAt          = Instant.now();
        return log;
    }

    // ── Enum ───────────────────────────────────────────────────────────────────

    public enum ArmouryAction {
        ISSUE, RETURN
    }
}
