package za.co.handyflow.platform.legalcompliance.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.AggregateRoot;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** A requirement-level consequence of a regulatory change that a person must review and resolve by a due date. */
@Entity
@Table(name = "legalcompliance_regulatory_impacts")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RegulatoryImpact extends AggregateRoot<RegulatoryImpact> {

    @Column(name = "change_id", nullable = false) private UUID changeId;

    @Enumerated(EnumType.STRING)
    @Column(name = "impact_type", nullable = false, length = 10)
    private ImpactType impactType;

    @Column(name = "old_requirement_id") private UUID oldRequirementId;
    @Column(name = "new_requirement_id") private UUID newRequirementId;
    @Column(nullable = false, length = 100) private String reference;
    @Column(nullable = false, length = 255) private String title;
    @Column(name = "diff_summary", columnDefinition = "TEXT") private String diffSummary;
    @Column(name = "obligation_id") private UUID obligationId;
    @Column(name = "old_assessment_id") private UUID oldAssessmentId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private ImpactStatus status = ImpactStatus.PENDING;

    @Enumerated(EnumType.STRING)
    @Column(length = 40)
    private ImpactOutcome outcome;

    @Column(name = "resolution_note", columnDefinition = "TEXT") private String resolutionNote;
    @Column(name = "resolved_by") private UUID resolvedBy;
    @Column(name = "resolved_by_name", length = 255) private String resolvedByName;
    @Column(name = "resolved_at") private Instant resolvedAt;
    @Column(name = "due_date", nullable = false) private LocalDate dueDate;

    public static RegulatoryImpact create(TenantId tenantId, UUID changeId, ImpactType type, UUID oldRequirementId,
                                          UUID newRequirementId, String reference, String title, String diffSummary,
                                          UUID obligationId, UUID oldAssessmentId, LocalDate dueDate) {
        RegulatoryImpact i = new RegulatoryImpact();
        i.initTenantId(tenantId);
        i.changeId = changeId;
        i.impactType = type;
        i.oldRequirementId = oldRequirementId;
        i.newRequirementId = newRequirementId;
        i.reference = reference;
        i.title = title;
        i.diffSummary = diffSummary;
        i.obligationId = obligationId;
        i.oldAssessmentId = oldAssessmentId;
        i.dueDate = dueDate;
        return i;
    }

    public void resolve(ImpactOutcome outcome, String note, UUID by, String byName) {
        if (status != ImpactStatus.PENDING)
            throw new IllegalStateException("Only a PENDING impact can be resolved — current: " + status);
        this.status = ImpactStatus.RESOLVED;
        this.outcome = outcome;
        this.resolutionNote = note;
        this.resolvedBy = by;
        this.resolvedByName = byName;
        this.resolvedAt = Instant.now();
    }

    public boolean isPending() { return status == ImpactStatus.PENDING; }
}
