package za.co.handyflow.platform.compliancetender.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.util.UUID;

/**
 * The frozen record of exactly what a tender looked like at the moment it
 * was submitted — the other half of the source material's "reference,
 * don't copy" principle: {@link TenderPersonnel} deliberately stays a
 * live reference so day-to-day editing always shows current HR data, but
 * a specific submission needs the opposite property — an answer to "what
 * did we actually submit six months ago?" that stays true even if the
 * referenced employee's details, or the requirement's status, changes
 * afterward. This entity is that answer: {@code snapshotJson} is a
 * complete, self-contained copy taken once, at submission time, and
 * never updated again.
 * <p>
 * Stored as a plain JSON string (not a structured JSONB Map the way
 * {@code Customer.address} is) — the snapshot shape is a nested object
 * (tender fields + a requirements list + a personnel list), not a flat
 * map, and Jackson's own {@code ObjectMapper} already handles that
 * serialization correctly without needing Hibernate's generic JSON type
 * mapping to understand the nested structure.
 * <p>
 * {@code snapshotNumber} exists because a tender can genuinely be
 * submitted more than once in its lifetime — {@code CLARIFICATION} loops
 * back toward a further submission in some procurement processes, and
 * each one deserves its own frozen record rather than overwriting the
 * last.
 */
@Entity
@Table(name = "tender_submission_snapshots")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TenderSubmissionSnapshot {

    @Id
    private UUID id = UUID.randomUUID();

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;

    @Column(name = "tender_id", nullable = false)
    private UUID tenderId;

    @Column(name = "snapshot_number", nullable = false)
    private int snapshotNumber;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "snapshot_json", columnDefinition = "jsonb", nullable = false)
    private String snapshotJson;

    @Column(name = "submitted_at", nullable = false, updatable = false)
    private Instant submittedAt;

    @Column(name = "submitted_by")
    private UUID submittedBy;

    public static TenderSubmissionSnapshot create(TenantId tenantId, UUID tenderId, int snapshotNumber,
                                                   String snapshotJson, UUID submittedBy) {
        TenderSubmissionSnapshot s = new TenderSubmissionSnapshot();
        s.tenantId = tenantId;
        s.tenderId = tenderId;
        s.snapshotNumber = snapshotNumber;
        s.snapshotJson = snapshotJson;
        s.submittedAt = Instant.now();
        s.submittedBy = submittedBy;
        return s;
    }
}
