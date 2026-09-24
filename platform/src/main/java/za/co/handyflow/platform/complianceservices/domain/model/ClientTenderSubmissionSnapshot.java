package za.co.handyflow.platform.complianceservices.domain.model;

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
 * The client-scoped counterpart to
 * compliancetender.TenderSubmissionSnapshot — same frozen-record
 * reasoning: a specific submission needs an answer to "what did we
 * actually submit for this client, on this date" that survives whatever
 * changes afterward. Same {@code @JdbcTypeCode(SqlTypes.JSON)} plain-
 * string storage, same per-tender {@code snapshotNumber} incrementing
 * rather than overwriting, for the identical reason — a tender can
 * genuinely be resubmitted after CLARIFICATION.
 * <p>
 * Now captures a personnel section too — added once the
 * personnel-reference design question was resolved (see
 * {@code ClientTenderPersonnel}'s own Javadoc), the same way
 * {@code compliancetender.TenderSubmissionSnapshot} already does.
 */
@Entity
@Table(name = "client_tender_submission_snapshots")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ClientTenderSubmissionSnapshot {

    @Id
    private UUID id = UUID.randomUUID();

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;

    @Column(name = "client_tender_id", nullable = false)
    private UUID clientTenderId;

    @Column(name = "snapshot_number", nullable = false)
    private int snapshotNumber;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "snapshot_json", columnDefinition = "jsonb", nullable = false)
    private String snapshotJson;

    @Column(name = "submitted_at", nullable = false, updatable = false)
    private Instant submittedAt;

    @Column(name = "submitted_by")
    private UUID submittedBy;

    public static ClientTenderSubmissionSnapshot create(TenantId tenantId, UUID clientTenderId, int snapshotNumber,
                                                         String snapshotJson, UUID submittedBy) {
        ClientTenderSubmissionSnapshot s = new ClientTenderSubmissionSnapshot();
        s.tenantId = tenantId;
        s.clientTenderId = clientTenderId;
        s.snapshotNumber = snapshotNumber;
        s.snapshotJson = snapshotJson;
        s.submittedAt = Instant.now();
        s.submittedBy = submittedBy;
        return s;
    }
}
