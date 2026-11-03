package za.co.handyflow.platform.documents.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

/**
 * The retention record of one business record that holds files (module + source type + source id). The owning module starts the
 * clock; the policy decides the period; a legal hold keeps it off the due list; a disposal approved by a second person deletes the
 * files and leaves this row (and the business record) as the tombstone. The database refuses any change once it is DISPOSED.
 */
@Entity
@Table(name = "document_retention_items")
public class RetentionItem {

    public static final String ACTIVE = "ACTIVE";
    public static final String PROPOSED = "PROPOSED";
    public static final String DISPOSED = "DISPOSED";

    @Id
    private UUID id;
    @Column(name = "tenant_id", nullable = false, updatable = false)   private UUID tenantId;
    @Column(name = "module_code", nullable = false, updatable = false) private String moduleCode;
    @Column(name = "source_type", nullable = false, updatable = false) private String sourceType;
    @Column(name = "source_id", nullable = false, updatable = false)   private UUID sourceId;
    @Column(name = "category", nullable = false)                       private String category;
    @Column(name = "jurisdiction", nullable = false)                   private String jurisdiction;
    @Column(name = "reference")                                        private String reference;
    @Column(name = "title", nullable = false)                          private String title;
    @Column(name = "context_label")                                    private String contextLabel;
    @Column(name = "clock_start")                                      private LocalDate clockStart;
    @Column(name = "retain_until")                                     private LocalDate retainUntil;
    @Column(name = "policy_years")                                     private Integer policyYears;
    @Column(name = "legal_hold", nullable = false)                     private boolean legalHold;
    @Column(name = "hold_reason")                                      private String holdReason;
    @Column(name = "hold_by")                                          private UUID holdBy;
    @Column(name = "hold_by_name")                                     private String holdByName;
    @Column(name = "hold_at")                                          private Instant holdAt;
    @Column(name = "status", nullable = false)                         private String status;
    @Column(name = "batch_id")                                         private UUID batchId;
    @Column(name = "disposed_at")                                      private Instant disposedAt;
    @Column(name = "disposal_detail")                                  private String disposalDetail;
    @Column(name = "created_at", nullable = false, updatable = false)  private Instant createdAt;
    @Column(name = "updated_at", nullable = false)                     private Instant updatedAt;

    protected RetentionItem() {}

    private static Instant now() { return Instant.now().truncatedTo(ChronoUnit.MICROS); }

    public static RetentionItem track(UUID tenantId, String module, String sourceType, UUID sourceId, String category, String jurisdiction) {
        RetentionItem i = new RetentionItem();
        i.id = UUID.randomUUID();
        i.tenantId = tenantId;
        i.moduleCode = RetentionRules.code(module, 40, "module");
        i.sourceType = RetentionRules.code(sourceType, 40, "source type");
        i.sourceId = sourceId;
        i.category = RetentionRules.code(category, 60, "category");
        i.jurisdiction = RetentionRules.jurisdiction(jurisdiction);
        i.status = ACTIVE;
        i.createdAt = now();
        i.updatedAt = i.createdAt;
        return i;
    }

    /** What the screen shows for it. Kept current while the record is alive; frozen once disposed. */
    public void describe(String reference, String title, String context) {
        requireNotDisposed();
        this.reference = RetentionRules.optional(reference, 150);
        String t = RetentionRules.optional(title, 300);
        this.title = t == null ? "(untitled)" : t;
        this.contextLabel = RetentionRules.optional(context, 200);
        touch();
    }

    /** Starts the clock once. A clock that has started is never moved back by the module (a re-closed project keeps its first date). */
    public boolean startClock(LocalDate start, int years) {
        requireNotDisposed();
        if (clockStart != null || start == null) return false;
        clockStart = start;
        applyPeriod(years);
        return true;
    }

    /** A policy changed: the period, and so the date, follow it. */
    public boolean applyPeriod(int years) {
        if (clockStart == null || DISPOSED.equals(status)) return false;
        LocalDate until = RetentionRules.retainUntil(clockStart, years);
        boolean changed = policyYears == null || policyYears != years || !until.equals(retainUntil);
        policyYears = years;
        retainUntil = until;
        if (changed) touch();
        return changed;
    }

    public void hold(String reason, UUID by, String byName) {
        requireNotDisposed();
        legalHold = true;
        holdReason = RetentionRules.reason(reason, "place a legal hold");
        holdBy = by;
        holdByName = RetentionRules.optional(byName, 200);
        holdAt = now();
        touch();
    }

    /** Lifting keeps who lifted it out of this row (the audit trail has it); the hold columns are cleared. */
    public void release() {
        requireNotDisposed();
        legalHold = false;
        holdReason = null;
        holdBy = null;
        holdByName = null;
        holdAt = null;
        touch();
    }

    public void propose(UUID batch) {
        if (!ACTIVE.equals(status)) throw new IllegalStateException("Only an active record can be proposed for disposal.");
        status = PROPOSED;
        batchId = batch;
        touch();
    }

    /** Back to active: the proposal was rejected or cancelled, the item was skipped, or its disposal failed. */
    public void returnToActive() {
        requireNotDisposed();
        status = ACTIVE;
        batchId = null;
        touch();
    }

    public void disposed(UUID batch, String detail) {
        if (!PROPOSED.equals(status) || !batch.equals(batchId)) throw new IllegalStateException("Only a record in this disposal request can be disposed.");
        if (legalHold) throw new IllegalStateException("A record under legal hold is never disposed.");
        status = DISPOSED;
        disposedAt = now();
        disposalDetail = RetentionRules.optional(detail, 1000);
        updatedAt = disposedAt;
    }

    public boolean isDue(LocalDate today) { return RetentionRules.isDue(status, legalHold, retainUntil, today); }
    public boolean isDisposed() { return DISPOSED.equals(status); }

    private void requireNotDisposed() {
        if (DISPOSED.equals(status)) throw new IllegalStateException("This record was disposed of and is final.");
    }
    private void touch() { updatedAt = now(); }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public String getModuleCode() { return moduleCode; }
    public String getSourceType() { return sourceType; }
    public UUID getSourceId() { return sourceId; }
    public String getCategory() { return category; }
    public String getJurisdiction() { return jurisdiction; }
    public String getReference() { return reference; }
    public String getTitle() { return title; }
    public String getContextLabel() { return contextLabel; }
    public LocalDate getClockStart() { return clockStart; }
    public LocalDate getRetainUntil() { return retainUntil; }
    public Integer getPolicyYears() { return policyYears; }
    public boolean isLegalHold() { return legalHold; }
    public String getHoldReason() { return holdReason; }
    public UUID getHoldBy() { return holdBy; }
    public String getHoldByName() { return holdByName; }
    public Instant getHoldAt() { return holdAt; }
    public String getStatus() { return status; }
    public UUID getBatchId() { return batchId; }
    public Instant getDisposedAt() { return disposedAt; }
    public String getDisposalDetail() { return disposalDetail; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
