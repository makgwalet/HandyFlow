package za.co.handyflow.platform.trainingprovider.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * One sampling draw: the rule and version used, a readable copy of the settings, the window, the seed and the counts. The picked records
 * are in the draw items. Closed with a decision and a written note once every picked record has been moderated; voided with a reason.
 */
@Entity(name = "TrainProvSamplingDraw")
@Table(name = "trainprov_sampling_draws")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TrainProvSamplingDraw {
    public static final String OPEN = "OPEN";
    public static final String CLOSED = "CLOSED";
    public static final String VOID = "VOID";

    @Id
    private UUID id = UUID.randomUUID();
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;
    @Column(name = "draw_no", nullable = false, updatable = false)
    private String drawNo;
    @Column(name = "rule_id", nullable = false, updatable = false)
    private UUID ruleId;
    @Column(name = "rule_version_id", nullable = false, updatable = false)
    private UUID ruleVersionId;
    @Column(name = "rule_snapshot", nullable = false, updatable = false)
    private String ruleSnapshot;
    @Column(name = "drawn_on", nullable = false, updatable = false)
    private LocalDate drawnOn;
    @Column(name = "decided_from", nullable = false, updatable = false)
    private LocalDate decidedFrom;
    @Column(name = "decided_to", nullable = false, updatable = false)
    private LocalDate decidedTo;
    @Column(name = "programme_id", updatable = false)
    private UUID programmeId;
    @Column(name = "cohort_id", updatable = false)
    private UUID cohortId;
    @Column(name = "assessment_type", updatable = false)
    private String assessmentType;
    @Column(nullable = false, updatable = false)
    private long seed;
    @Column(name = "eligible_count", nullable = false, updatable = false)
    private int eligibleCount;
    @Column(name = "required_count", nullable = false, updatable = false)
    private int requiredCount;
    @Column(name = "selected_count", nullable = false, updatable = false)
    private int selectedCount;
    @Column(nullable = false)
    private String status = OPEN;
    @Column(name = "drawn_by", nullable = false, updatable = false)
    private UUID drawnBy;
    @Column(name = "drawn_at", nullable = false, updatable = false)
    private Instant drawnAt;
    private String decision;
    @Column(name = "decision_note")
    private String decisionNote;
    @Column(name = "decided_by")
    private UUID decidedBy;
    @Column(name = "decided_at")
    private Instant decidedAt;
    private String summary;
    @Column(name = "void_reason")
    private String voidReason;
    @Column(name = "void_by")
    private UUID voidBy;
    @Column(name = "void_at")
    private Instant voidAt;
    @Version
    private Long version;

    public static TrainProvSamplingDraw create(TenantId t, String no, UUID ruleId, UUID versionId, String snapshot, LocalDate drawnOn, LocalDate from, LocalDate to,
                                               UUID programmeId, UUID cohortId, String assessmentType, long seed, int eligible, int required, int selected, UUID by) {
        TrainProvSamplingDraw d = new TrainProvSamplingDraw();
        d.tenantId = t.getValue();
        d.drawNo = no;
        d.ruleId = ruleId;
        d.ruleVersionId = versionId;
        d.ruleSnapshot = snapshot;
        d.drawnOn = drawnOn;
        d.decidedFrom = from;
        d.decidedTo = to;
        d.programmeId = programmeId;
        d.cohortId = cohortId;
        d.assessmentType = assessmentType;
        d.seed = seed;
        d.eligibleCount = eligible;
        d.requiredCount = required;
        d.selectedCount = selected;
        d.drawnBy = by;
        d.drawnAt = Instant.now();
        return d;
    }

    public boolean isOpen() {
        return OPEN.equals(status);
    }

    public void close(String decision, String note, String summary, UUID by) {
        if (!isOpen()) {
            throw new IllegalStateException("This draw is not open.");
        }
        this.status = CLOSED;
        this.decision = decision;
        this.decisionNote = note.trim();
        this.summary = summary;
        this.decidedBy = by;
        this.decidedAt = Instant.now();
    }

    public void voidDraw(String reason, UUID by) {
        if (!isOpen()) {
            throw new IllegalStateException("Only an open draw can be voided.");
        }
        if (reason == null || reason.trim().length() < 10) {
            throw new IllegalArgumentException("Give a reason of at least 10 characters.");
        }
        this.status = VOID;
        this.voidReason = reason.trim();
        this.voidBy = by;
        this.voidAt = Instant.now();
    }
}
