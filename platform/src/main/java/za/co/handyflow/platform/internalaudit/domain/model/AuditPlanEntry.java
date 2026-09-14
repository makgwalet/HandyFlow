package za.co.handyflow.platform.internalaudit.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * One universe entry selected into an annual plan. rationale is free
 * text, deliberately not constrained to "cite the risk assessment" —
 * an entry can be selected for other real reasons (regulatory mandate,
 * board request) even when risk-based prioritization is the common
 * case, matching the product owner's own note that risk and audit
 * selection are related but not identical concepts.
 */
@Entity
@Table(name = "audit_plan_entries")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AuditPlanEntry {

    @Id
    private UUID id = UUID.randomUUID();

    @Column(name = "tenant_id", nullable = false) private UUID tenantId;
    @Column(name = "plan_id", nullable = false) private UUID planId;
    @Column(name = "universe_entry_id", nullable = false) private UUID universeEntryId;
    @Column(name = "risk_assessment_id") private UUID riskAssessmentId;
    @Column(name = "planned_quarter") private Integer plannedQuarter;
    @Column(name = "rationale") private String rationale;
    @Column(name = "status", nullable = false) private String status = "PLANNED"; // PLANNED | IN_PROGRESS | COMPLETED | DEFERRED
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;

    public static AuditPlanEntry create(UUID tenantId, UUID planId, UUID universeEntryId,
                                        UUID riskAssessmentId, Integer plannedQuarter, String rationale) {
        AuditPlanEntry e = new AuditPlanEntry();
        e.tenantId = tenantId;
        e.planId = planId;
        e.universeEntryId = universeEntryId;
        e.riskAssessmentId = riskAssessmentId;
        e.plannedQuarter = plannedQuarter;
        e.rationale = rationale;
        e.createdAt = Instant.now();
        return e;
    }

    public void updateStatus(String status) {
        this.status = status;
    }
}
