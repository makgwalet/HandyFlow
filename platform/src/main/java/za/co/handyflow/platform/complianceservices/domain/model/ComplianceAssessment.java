package za.co.handyflow.platform.complianceservices.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** An assessment of one client on one date. Completed assessments are frozen, including the weights they were scored with. */
@Entity
@Table(name = "compliance_assessments")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ComplianceAssessment {

    @Id
    private UUID id = UUID.randomUUID();

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;

    @Column(name = "client_id", nullable = false)
    private UUID clientId;

    @Column(nullable = false)
    private String status = "DRAFT";

    @Column(name = "as_of", nullable = false)
    private LocalDate asOf;

    @Column(name = "w_low", nullable = false)
    private int weightLow;
    @Column(name = "w_medium", nullable = false)
    private int weightMedium;
    @Column(name = "w_high", nullable = false)
    private int weightHigh;
    @Column(name = "w_critical", nullable = false)
    private int weightCritical;

    @Column(name = "score_percent", precision = 5, scale = 1)
    private BigDecimal scorePercent;

    @Column(name = "scored_count")
    private Integer scoredCount;

    @Column(name = "unscored_count")
    private Integer unscoredCount;

    @Column(name = "summary_note", columnDefinition = "TEXT")
    private String summaryNote;

    @Column(name = "started_by")
    private UUID startedBy;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt = Instant.now();

    @Column(name = "completed_by")
    private UUID completedBy;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Version
    private Long version;

    public static ComplianceAssessment start(TenantId tenantId, UUID clientId, LocalDate asOf, AssessmentRules.Weights w, UUID by) {
        ComplianceAssessment a = new ComplianceAssessment();
        a.tenantId = tenantId;
        a.clientId = clientId;
        a.asOf = asOf;
        a.weightLow = w.low();
        a.weightMedium = w.medium();
        a.weightHigh = w.high();
        a.weightCritical = w.critical();
        a.startedBy = by;
        return a;
    }

    public boolean isDraft() { return "DRAFT".equals(status); }

    public AssessmentRules.Weights weights() { return new AssessmentRules.Weights(weightLow, weightMedium, weightHigh, weightCritical); }

    public void requireDraft() {
        if (!isDraft()) throw new IllegalStateException("This assessment is completed and can no longer change");
    }

    public void complete(AssessmentRules.Score score, String note, UUID by) {
        requireDraft();
        if (by == null) throw new IllegalArgumentException("A person is required");
        this.scorePercent = score.percent();
        this.scoredCount = score.scored();
        this.unscoredCount = score.unscored();
        this.summaryNote = note == null || note.isBlank() ? null : ClientProfileRules.clean(note, 4000);
        this.status = "COMPLETED";
        this.completedBy = by;
        this.completedAt = Instant.now();
    }
}
