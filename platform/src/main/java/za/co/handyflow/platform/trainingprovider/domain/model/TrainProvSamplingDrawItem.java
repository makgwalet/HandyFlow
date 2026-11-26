package za.co.handyflow.platform.trainingprovider.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.util.UUID;

/** One attempt in a draw's eligible population, with whether it was picked and why. Written once with the draw. */
@Entity(name = "TrainProvSamplingDrawItem")
@Table(name = "trainprov_sampling_draw_items")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TrainProvSamplingDrawItem {
    @Id
    private UUID id = UUID.randomUUID();
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;
    @Column(name = "draw_id", nullable = false, updatable = false)
    private UUID drawId;
    @Column(name = "attempt_id", nullable = false, updatable = false)
    private UUID attemptId;
    @Column(name = "assessor_id", nullable = false, updatable = false)
    private UUID assessorId;
    @Column(name = "risk_score", nullable = false, updatable = false)
    private int riskScore;
    @Column(name = "risk_factors", updatable = false)
    private String riskFactors;
    @Column(nullable = false, updatable = false)
    private boolean selected;
    @Column(name = "selected_reason", updatable = false)
    private String selectedReason;
    @Column(name = "target_note", updatable = false)
    private String targetNote;
    @Column(name = "skipped_reason")
    private String skippedReason;

    public static TrainProvSamplingDrawItem create(TenantId t, UUID drawId, UUID attemptId, UUID assessorId, int riskScore, String riskFactors, boolean selected,
                                                   String reason, String targetNote) {
        TrainProvSamplingDrawItem i = new TrainProvSamplingDrawItem();
        i.tenantId = t.getValue();
        i.drawId = drawId;
        i.attemptId = attemptId;
        i.assessorId = assessorId;
        i.riskScore = riskScore;
        i.riskFactors = riskFactors == null || riskFactors.isBlank() ? null : riskFactors;
        i.selected = selected;
        i.selectedReason = selected ? reason : null;
        i.targetNote = selected ? targetNote : null;
        return i;
    }
}
