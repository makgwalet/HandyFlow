package za.co.handyflow.platform.trainingprovider.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;
import za.co.handyflow.platform.trainingprovider.domain.rules.CohortRules;

import java.time.Instant;
import java.util.UUID;

/** A room or training area at a delivery site. Never deleted: made inactive with a reason. */
@Entity(name = "TrainProvVenue")
@Table(name = "trainprov_venues")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TrainProvVenue {
    @Id
    private UUID id = UUID.randomUUID();
    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;
    @Column(name = "site_id", nullable = false)
    private UUID siteId;
    @Column(nullable = false)
    private String name;
    private Integer capacity;
    private String notes;
    @Column(nullable = false)
    private String status = "ACTIVE";
    @Column(name = "inactive_reason")
    private String inactiveReason;
    @Column(name = "created_by", nullable = false)
    private UUID createdBy;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
    @Version
    private Long version;

    public static TrainProvVenue create(TenantId t, UUID siteId, String name, Integer capacity, String notes, UUID by) {
        String problem = CohortRules.venueProblem(name, capacity);
        if (problem != null) {
            throw new IllegalArgumentException(problem);
        }
        TrainProvVenue v = new TrainProvVenue();
        v.tenantId = t.getValue();
        v.siteId = siteId;
        v.name = name.trim();
        v.capacity = capacity;
        v.notes = notes == null || notes.isBlank() ? null : notes.trim();
        v.createdBy = by;
        v.createdAt = Instant.now();
        v.updatedAt = v.createdAt;
        return v;
    }

    public void update(String name, Integer capacity, String notes) {
        String problem = CohortRules.venueProblem(name, capacity);
        if (problem != null) {
            throw new IllegalArgumentException(problem);
        }
        this.name = name.trim();
        this.capacity = capacity;
        this.notes = notes == null || notes.isBlank() ? null : notes.trim();
        this.updatedAt = Instant.now();
    }

    public void deactivate(String reason) {
        String problem = CohortRules.reasonProblem(reason);
        if (problem != null) {
            throw new IllegalArgumentException(problem);
        }
        if (!"ACTIVE".equals(status)) {
            throw new IllegalStateException("This venue is already inactive");
        }
        this.status = "INACTIVE";
        this.inactiveReason = reason.trim();
        this.updatedAt = Instant.now();
    }

    public void reactivate() {
        if (!"INACTIVE".equals(status)) {
            throw new IllegalStateException("This venue is already active");
        }
        this.status = "ACTIVE";
        this.inactiveReason = null;
        this.updatedAt = Instant.now();
    }
}
