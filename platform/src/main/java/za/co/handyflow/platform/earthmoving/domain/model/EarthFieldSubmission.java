package za.co.handyflow.platform.earthmoving.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** One entry sent from a phone, and what the server decided about it. The entry itself is never changed; only a conflict can be reviewed. */
@Entity
@Table(name = "earthmoving_field_submissions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EarthFieldSubmission {

    @Id
    private UUID id = UUID.randomUUID();

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;

    @Column(name = "submission_id", nullable = false)
    private UUID submissionId;

    @Column(nullable = false)
    private String kind;

    @Column(name = "asset_id")
    private UUID assetId;

    @Column(name = "captured_at", nullable = false)
    private Instant capturedAt;

    @Column(name = "received_at", nullable = false)
    private Instant receivedAt;

    @Column(name = "submitted_by", nullable = false)
    private UUID submittedBy;

    @Column(name = "meter_value", precision = 12, scale = 2)
    private BigDecimal meterValue;

    @Column(precision = 9, scale = 6)
    private BigDecimal latitude;

    @Column(precision = 9, scale = 6)
    private BigDecimal longitude;

    @Column(columnDefinition = "TEXT")
    private String note;

    @Column(nullable = false)
    private String status;

    @Column(columnDefinition = "TEXT")
    private String reason;

    @Column(name = "reviewed_by")
    private UUID reviewedBy;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    @Column(name = "review_decision")
    private String reviewDecision;

    @Column(name = "review_note", columnDefinition = "TEXT")
    private String reviewNote;

    public static EarthFieldSubmission received(TenantId tenantId, UUID submissionId, String kind, UUID assetId, Instant capturedAt, BigDecimal meter,
                                                BigDecimal latitude, BigDecimal longitude, String note, UUID actor, String status, String reason) {
        if (submissionId == null) throw new IllegalArgumentException("Every entry needs its own submission id.");
        if (!FieldRules.SYNCED.equals(status) && (reason == null || reason.isBlank())) throw new IllegalArgumentException("A refused or conflicting entry needs a reason.");
        EarthFieldSubmission s = new EarthFieldSubmission();
        s.tenantId = tenantId;
        s.submissionId = submissionId;
        s.kind = kind;
        s.assetId = assetId;
        s.capturedAt = capturedAt == null ? Instant.now() : capturedAt;
        s.receivedAt = Instant.now();
        s.submittedBy = actor;
        s.meterValue = meter;
        s.latitude = latitude;
        s.longitude = longitude;
        s.note = FieldRules.note(note, 1000);
        s.status = status;
        s.reason = reason;
        return s;
    }

    public boolean isOpenConflict() { return FieldRules.CONFLICT.equals(status) && reviewDecision == null; }

    /** Records the reviewer's decision on a conflict. Whether the entry was applied is decided by the caller before this is called. */
    public void review(String decision, String reviewNote, UUID actor) {
        if (!isOpenConflict()) throw new IllegalStateException("Only a conflict that has not been reviewed can be reviewed.");
        this.reviewDecision = FieldRules.decision(decision);
        this.reviewNote = FieldRules.note(reviewNote, 1000);
        this.reviewedBy = actor;
        this.reviewedAt = Instant.now();
    }
}
