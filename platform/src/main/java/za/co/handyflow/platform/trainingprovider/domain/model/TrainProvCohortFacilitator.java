package za.co.handyflow.platform.trainingprovider.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;
import za.co.handyflow.platform.trainingprovider.domain.rules.CohortRules;

import java.time.Instant;
import java.util.UUID;

/** A facilitator assigned to a cohort, with the appointment that made them eligible on the day. Removed with a reason, never deleted. */
@Entity(name = "TrainProvCohortFacilitator")
@Table(name = "trainprov_cohort_facilitators")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TrainProvCohortFacilitator {
    @Id
    private UUID id = UUID.randomUUID();
    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;
    @Column(name = "cohort_id", nullable = false)
    private UUID cohortId;
    @Column(name = "practitioner_id", nullable = false)
    private UUID practitionerId;
    @Column(name = "appointment_id", nullable = false)
    private UUID appointmentId;
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

    public static TrainProvCohortFacilitator create(TenantId t, UUID cohortId, UUID practitionerId, UUID appointmentId, UUID by) {
        TrainProvCohortFacilitator f = new TrainProvCohortFacilitator();
        f.tenantId = t.getValue();
        f.cohortId = cohortId;
        f.practitionerId = practitionerId;
        f.appointmentId = appointmentId;
        f.addedBy = by;
        f.addedAt = Instant.now();
        return f;
    }

    public void remove(String reason, UUID by) {
        String problem = CohortRules.reasonProblem(reason);
        if (problem != null) {
            throw new IllegalArgumentException(problem);
        }
        if (!"ACTIVE".equals(status)) {
            throw new IllegalStateException("This facilitator has already been removed from the cohort");
        }
        this.status = "REMOVED";
        this.removedReason = reason.trim();
        this.removedBy = by;
        this.removedAt = Instant.now();
    }
}
