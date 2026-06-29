// security/domain/model/RotationPattern.java

package za.co.handyflow.platform.security.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * RotationPattern — a named recurring shift schedule for a site.
 *
 * Materialises into actual Shift rows via RotationScheduleService.generateNextPeriod().
 * The pattern itself is immutable once shifts have been generated from it —
 * change the pattern, then use "end current + create new" workflow so generated
 * shifts don't retroactively change.
 *
 * Pattern types and their cycle_definition shape:
 *
 *   FIXED_DAYS_ON_OFF     : {"onDays": 4, "offDays": 2}
 *   ALTERNATING_DAY_NIGHT : {"cycleWeeks": 2, "dayStart": "06:00", "nightStart": "18:00"}
 *   WEEKLY_FIXED          : {"monday": "DAY", "tuesday": "OFF", ... "sunday": "NIGHT"}
 *   CUSTOM                : free-form, interpreted by the schedule generator
 */
@Entity
@Table(name = "security_rotation_patterns")
@Getter
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class RotationPattern {

    @Id
    private UUID id = UUID.randomUUID();

    @Embedded
    @AttributeOverride(name = "value",
            column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;

    @Column(name = "site_id", nullable = false)
    private UUID siteId;

    @Column(nullable = false, length = 120)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "pattern_type", nullable = false, length = 30)
    private PatternType patternType;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "cycle_definition", columnDefinition = "jsonb")
    private Map<String, Object> cycleDefinition;

    @Column(name = "shift_length_hours", nullable = false)
    private int shiftLengthHours = 12;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    // ── Factory ────────────────────────────────────────────────────────────────

    public static RotationPattern create(
            TenantId tenantId, UUID siteId, String name,
            PatternType patternType, Map<String, Object> cycleDefinition,
            int shiftLengthHours) {
        RotationPattern p = new RotationPattern();
        p.tenantId          = tenantId;
        p.siteId            = siteId;
        p.name              = name.strip();
        p.patternType       = patternType;
        p.cycleDefinition   = cycleDefinition;
        p.shiftLengthHours  = shiftLengthHours;
        p.active            = true;
        p.createdAt         = Instant.now();
        p.updatedAt         = Instant.now();
        return p;
    }

    // ── Mutations ──────────────────────────────────────────────────────────────

    public void update(String name, Map<String, Object> cycleDefinition, int shiftLengthHours) {
        this.name              = name.strip();
        this.cycleDefinition   = cycleDefinition;
        this.shiftLengthHours  = shiftLengthHours;
        this.updatedAt         = Instant.now();
    }

    public void deactivate() {
        this.active    = false;
        this.updatedAt = Instant.now();
    }

    // ── Enums ──────────────────────────────────────────────────────────────────

    public enum PatternType {
        FIXED_DAYS_ON_OFF,
        ALTERNATING_DAY_NIGHT,
        WEEKLY_FIXED,
        CUSTOM
    }
}
