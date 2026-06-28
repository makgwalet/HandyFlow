package za.co.handyflow.platform.crm.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import za.co.handyflow.platform.crm.dto.ImportJobResult.RowError;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * ImportJob — tracks the lifecycle of a CSV import operation.
 *
 * WHY an entity and not just a record in memory?
 * The import is asynchronous.  The HTTP request returns immediately with
 * the job ID.  The client polls GET /import/{jobId} for results.  That
 * poll is a separate HTTP request — possibly from a different server
 * instance.  We need the job state persisted so any instance can answer it.
 *
 * State machine:  PENDING → PROCESSING → DONE | FAILED
 * Domain methods (markProcessing, markDone, markFailed) enforce legal
 * transitions — the same "always-valid entity" pattern as Customer.
 */
@Entity
@Table(name = "customer_import_jobs")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ImportJob {

    public enum Status { PENDING, PROCESSING, DONE, FAILED }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status = Status.PENDING;

    @Column(length = 255)
    private String filename;

    @Column(name = "total_rows", nullable = false)
    private int totalRows;

    @Column(name = "created_count", nullable = false)
    private int createdCount;

    @Column(name = "skipped_count", nullable = false)
    private int skippedCount;

    @Column(name = "error_count", nullable = false)
    private int errorCount;

    /**
     * Per-row errors stored as JSONB.
     * WHY not a child table?
     * Row errors are only ever read as a complete list alongside the job.
     * They're never queried individually.  JSONB is simpler and avoids
     * a join on every status poll.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "row_errors", columnDefinition = "jsonb")
    private List<RowError> rowErrors;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "created_by")
    private UUID createdBy;

    // ── Factory ───────────────────────────────────────────────────────────────

    public static ImportJob create(TenantId tenantId, String filename, UUID createdBy) {
        var job       = new ImportJob();
        job.tenantId  = tenantId;
        job.filename  = filename;
        job.createdBy = createdBy;
        job.status    = Status.PENDING;
        job.createdAt = Instant.now();
        return job;
    }

    // ── Domain state transitions ──────────────────────────────────────────────

    public void markProcessing() {
        this.status    = Status.PROCESSING;
        this.startedAt = Instant.now();
    }

    public void markDone(int totalRows, int created, int skipped, List<RowError> errors) {
        this.status       = Status.DONE;
        this.totalRows    = totalRows;
        this.createdCount = created;
        this.skippedCount = skipped;
        this.errorCount   = errors.size();
        this.rowErrors    = errors;
        this.completedAt  = Instant.now();
    }

    public void markFailed(String reason) {
        this.status      = Status.FAILED;
        this.rowErrors   = List.of(new RowError(0, "", reason));
        this.completedAt = Instant.now();
    }
}
