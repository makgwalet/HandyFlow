package za.co.handyflow.platform.recruitmentagency.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * Audit trail of stage transitions for one placement — mirrors
 * recruiter's own RecStageHistory pattern for the internal hiring
 * pipeline, applied to the agency's client-facing one.
 */
@Entity
@Table(name = "reca_placement_stage_history")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RecAgencyPlacementStageHistory {

    @Id
    private UUID id = UUID.randomUUID();

    @Column(name = "placement_id", nullable = false)
    private UUID placementId;

    @Column(name = "from_stage")
    private String fromStage; // null for the initial SUBMITTED entry

    @Column(name = "to_stage", nullable = false)
    private String toStage;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    @Column(name = "changed_by")
    private UUID changedBy;

    @Column(name = "changed_at", nullable = false, updatable = false)
    private Instant changedAt;

    public static RecAgencyPlacementStageHistory record(UUID placementId, String fromStage,
                                                        String toStage, String notes, UUID changedBy) {
        RecAgencyPlacementStageHistory h = new RecAgencyPlacementStageHistory();
        h.placementId = placementId;
        h.fromStage = fromStage;
        h.toStage = toStage;
        h.notes = notes;
        h.changedBy = changedBy;
        h.changedAt = Instant.now();
        return h;
    }
}