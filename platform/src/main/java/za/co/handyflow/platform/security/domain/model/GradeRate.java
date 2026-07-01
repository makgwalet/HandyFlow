// security/domain/model/GradeRate.java
package za.co.handyflow.platform.security.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * GradeRate — tenant-configurable default hourly rate for each PSiRA grade.
 *
 * WHY grade-based defaults with per-guard overrides?
 * South African security companies pay guards according to their PSiRA grade
 * (A through E), set by the PSIRA sector determinations. Most guards at the
 * same grade earn the same rate; individual overrides handle seniority or
 * special allowances without needing a full HR system.
 *
 * Rate precedence at payroll computation time:
 *   1. Guard.hourlyRateCents (explicit override) — takes precedence if set
 *   2. GradeRate for the guard's grade on the period end date — fallback
 *   3. If neither exists: PayrollService throws and blocks period approval
 *      until rates are configured.
 */
@Entity
@Table(name = "security_grade_rates")
@Getter
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class GradeRate {

    @Id
    private UUID id = UUID.randomUUID();

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;

    @Column(nullable = false, length = 5)
    private String grade;   // A | B | C | D | E

    @Column(name = "hourly_rate_cents", nullable = false)
    private int hourlyRateCents;

    @Column(name = "standard_hours_per_day", nullable = false)
    private int standardHoursPerDay = 9;    // overtime kicks in after this

    @Column(name = "effective_from", nullable = false)
    private LocalDate effectiveFrom;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "created_by")
    private UUID createdBy;

    public static GradeRate create(TenantId tenantId, String grade, int hourlyRateCents,
                                   int standardHoursPerDay, LocalDate effectiveFrom,
                                   UUID createdBy) {
        GradeRate r          = new GradeRate();
        r.tenantId           = tenantId;
        r.grade              = grade.toUpperCase();
        r.hourlyRateCents    = hourlyRateCents;
        r.standardHoursPerDay = standardHoursPerDay;
        r.effectiveFrom      = effectiveFrom;
        r.createdBy          = createdBy;
        r.createdAt          = Instant.now();
        return r;
    }
}
