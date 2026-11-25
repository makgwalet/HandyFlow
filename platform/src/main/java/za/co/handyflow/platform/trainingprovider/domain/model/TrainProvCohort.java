package za.co.handyflow.platform.trainingprovider.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;
import za.co.handyflow.platform.trainingprovider.domain.rules.CohortRules;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** A group of learners taking one programme version together at one site. Frozen to the version and site it was opened on. */
@Entity(name = "TrainProvCohort")
@Table(name = "trainprov_cohorts")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TrainProvCohort {
    @Id
    private UUID id = UUID.randomUUID();
    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;
    @Column(name = "cohort_no", nullable = false)
    private String cohortNo;
    @Column(nullable = false)
    private String name;
    @Column(name = "programme_id", nullable = false)
    private UUID programmeId;
    @Column(name = "programme_version_id", nullable = false)
    private UUID programmeVersionId;
    @Column(name = "site_id", nullable = false)
    private UUID siteId;
    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;
    @Column(name = "end_date")
    private LocalDate endDate;
    private Integer capacity;
    private String notes;
    @Column(nullable = false)
    private String status = CohortRules.PLANNED;
    @Column(name = "status_reason")
    private String statusReason;
    @Column(name = "created_by", nullable = false)
    private UUID createdBy;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
    @Version
    private Long version;

    public static TrainProvCohort create(TenantId t, String cohortNo, String name, UUID programmeId, UUID programmeVersionId, UUID siteId,
                                         LocalDate start, LocalDate end, Integer capacity, String notes, UUID by) {
        String problem = CohortRules.cohortProblem(name, start, end, capacity);
        if (problem != null) {
            throw new IllegalArgumentException(problem);
        }
        TrainProvCohort c = new TrainProvCohort();
        c.tenantId = t.getValue();
        c.cohortNo = cohortNo;
        c.name = name.trim();
        c.programmeId = programmeId;
        c.programmeVersionId = programmeVersionId;
        c.siteId = siteId;
        c.startDate = start;
        c.endDate = end;
        c.capacity = capacity;
        c.notes = notes == null || notes.isBlank() ? null : notes.trim();
        c.createdBy = by;
        c.createdAt = Instant.now();
        c.updatedAt = c.createdAt;
        return c;
    }

    public void update(String name, LocalDate start, LocalDate end, Integer capacity, String notes, long activeMembers) {
        if (!CohortRules.open(status)) {
            throw new IllegalStateException("A " + status.toLowerCase() + " cohort cannot be changed");
        }
        String problem = CohortRules.cohortProblem(name, start, end, capacity);
        if (problem != null) {
            throw new IllegalArgumentException(problem);
        }
        if (capacity != null && capacity < activeMembers) {
            throw new IllegalStateException("The cohort already has " + activeMembers + " learners, so capacity cannot be lower than that");
        }
        this.name = name.trim();
        this.startDate = start;
        this.endDate = end;
        this.capacity = capacity;
        this.notes = notes == null || notes.isBlank() ? null : notes.trim();
        this.updatedAt = Instant.now();
    }

    public void moveTo(String to, String reason) {
        String problem = CohortRules.transitionProblem(status, to, reason);
        if (problem != null) {
            throw new IllegalStateException(problem);
        }
        this.status = to;
        this.statusReason = reason == null || reason.isBlank() ? null : reason.trim();
        this.updatedAt = Instant.now();
    }
}
