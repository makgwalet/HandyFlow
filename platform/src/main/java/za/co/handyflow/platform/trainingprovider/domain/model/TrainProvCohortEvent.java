package za.co.handyflow.platform.trainingprovider.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.util.UUID;

/** One line of a cohort's history. Written once, never changed. */
@Entity(name = "TrainProvCohortEvent")
@Table(name = "trainprov_cohort_events")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TrainProvCohortEvent {
    @Id
    private UUID id = UUID.randomUUID();
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;
    @Column(name = "cohort_id", nullable = false, updatable = false)
    private UUID cohortId;
    @Column(nullable = false, updatable = false)
    private String action;
    @Column(updatable = false)
    private String note;
    @Column(name = "acted_by", nullable = false, updatable = false)
    private UUID actedBy;
    @Column(name = "acted_at", nullable = false, updatable = false)
    private Instant actedAt;

    public static TrainProvCohortEvent of(TenantId t, UUID cohortId, String action, String note, UUID by) {
        TrainProvCohortEvent e = new TrainProvCohortEvent();
        e.tenantId = t.getValue();
        e.cohortId = cohortId;
        e.action = action;
        e.note = note == null || note.isBlank() ? null : note.trim();
        e.actedBy = by;
        e.actedAt = Instant.now();
        return e;
    }
}
