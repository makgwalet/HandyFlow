package za.co.handyflow.platform.trainingprovider.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;
import za.co.handyflow.platform.trainingprovider.domain.rules.CohortRules;

import java.time.Instant;
import java.util.UUID;

/** A learner enrolment placed in a cohort. Removed with a reason, never deleted. */
@Entity(name = "TrainProvCohortMember")
@Table(name = "trainprov_cohort_members")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TrainProvCohortMember {
    @Id
    private UUID id = UUID.randomUUID();
    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;
    @Column(name = "cohort_id", nullable = false)
    private UUID cohortId;
    @Column(name = "enrolment_id", nullable = false)
    private UUID enrolmentId;
    @Column(nullable = false)
    private String status = "ACTIVE";
    @Column(name = "removed_reason")
    private String removedReason;
    @Column(name = "removed_by")
    private UUID removedBy;
    @Column(name = "removed_at")
    private Instant removedAt;
    @Column(name = "added_by", nullable = false)
    private UUID addedBy;
    @Column(name = "added_at", nullable = false)
    private Instant addedAt;
    @Version
    private Long version;

    public static TrainProvCohortMember create(TenantId t, UUID cohortId, UUID enrolmentId, UUID by) {
        TrainProvCohortMember m = new TrainProvCohortMember();
        m.tenantId = t.getValue();
        m.cohortId = cohortId;
        m.enrolmentId = enrolmentId;
        m.addedBy = by;
        m.addedAt = Instant.now();
        return m;
    }

    public void remove(String reason, UUID by) {
        String problem = CohortRules.reasonProblem(reason);
        if (problem != null) {
            throw new IllegalArgumentException(problem);
        }
        if (!"ACTIVE".equals(status)) {
            throw new IllegalStateException("This learner has already been removed from the cohort");
        }
        this.status = "REMOVED";
        this.removedReason = reason.trim();
        this.removedBy = by;
        this.removedAt = Instant.now();
    }
}
