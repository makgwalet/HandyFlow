// security/domain/model/GuardScreeningRecord.java

package za.co.handyflow.platform.security.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * GuardScreeningRecord — a single vetting/screening event for a guard.
 *
 * One row per test, never overwritten — a guard's full screening history
 * (every polygraph date and result, not just the most recent) is preserved
 * permanently so a company can justify a guard's continued deployment if
 * ever challenged by a client or regulator.
 *
 * General-purpose at the core guard level (not VIP/CP-specific) — Phase 3's
 * close-protection vetting tier reads from this same table filtered to the
 * relevant screening types, rather than duplicating the model.
 *
 * WHY reportRef instead of storing the report content?
 * The screening report (polygraph transcript, criminal record extract) is
 * sensitive and potentially large. Store a pointer (S3 key, vault reference)
 * here, never the document content in a plain DB column.
 */
@Entity
@Table(name = "security_guard_screening_records")
@Getter
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class GuardScreeningRecord {

    @Id
    private UUID id = UUID.randomUUID();

    @Embedded
    @AttributeOverride(name = "value",
            column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;

    @Column(name = "guard_id", nullable = false)
    private UUID guardId;

    @Enumerated(EnumType.STRING)
    @Column(name = "screening_type", nullable = false, length = 30)
    private ScreeningType screeningType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ScreeningReason reason;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ScreeningResult result = ScreeningResult.PENDING;

    @Column(name = "conducted_by", length = 200)
    private String conductedBy;

    @Column(name = "conducted_at")
    private LocalDate conductedAt;

    @Column(name = "next_due_at")
    private LocalDate nextDueAt;

    @Column(name = "report_ref")
    private String reportRef;

    @Column
    private String notes;

    @Column(length = 200)
    private String provider;

    @Column(name = "requested_at")
    private LocalDate requestedAt;

    /** CLEARED | NOT_CLEARED. Set by a reviewer after a result exists; cleared again if the result is re-recorded. */
    @Column(length = 20)
    private String decision;

    @Column(name = "decision_note", columnDefinition = "text")
    private String decisionNote;

    @Column(name = "decided_by")
    private UUID decidedBy;

    @Column(name = "decided_by_name", length = 200)
    private String decidedByName;

    @Column(name = "decided_at")
    private Instant decidedAt;

    @Column(name = "created_by")
    private UUID createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    // ── Factory ────────────────────────────────────────────────────────────────

    public static GuardScreeningRecord create(TenantId tenantId, UUID guardId,
                                              ScreeningType type, ScreeningReason reason,
                                              UUID createdBy) {
        GuardScreeningRecord r = new GuardScreeningRecord();
        r.tenantId             = tenantId;
        r.guardId              = guardId;
        r.screeningType        = type;
        r.reason               = reason;
        r.result               = ScreeningResult.PENDING;
        r.createdBy            = createdBy;
        r.createdAt            = Instant.now();
        r.updatedAt            = Instant.now();
        return r;
    }

    // ── Mutations ──────────────────────────────────────────────────────────────

    public void recordResult(ScreeningResult result, String conductedBy,
                             LocalDate conductedAt, LocalDate nextDueAt,
                             String reportRef, String notes) {
        this.result      = result;
        this.conductedBy = conductedBy;
        this.conductedAt = conductedAt;
        this.nextDueAt   = nextDueAt;
        this.reportRef   = reportRef;
        this.notes       = notes;
        // A new result invalidates any earlier sign-off: the reviewer approved a different result.
        this.decision      = null;
        this.decisionNote  = null;
        this.decidedBy     = null;
        this.decidedByName = null;
        this.decidedAt     = null;
        this.updatedAt   = Instant.now();
    }

    /** Request details captured when the screening is ordered. */
    public void setRequestDetails(String provider, LocalDate requestedAt) {
        this.provider    = provider == null || provider.isBlank() ? null : provider.trim();
        this.requestedAt = requestedAt;
        this.updatedAt   = Instant.now();
    }

    /** Reviewer sign-off. Only possible once a result has been recorded. */
    public void decide(String decision, String note, UUID by, String byName) {
        if (result == ScreeningResult.PENDING) {
            throw new IllegalStateException("A decision needs a recorded result first");
        }
        if (!"CLEARED".equals(decision) && !"NOT_CLEARED".equals(decision)) {
            throw new IllegalArgumentException("Decision must be CLEARED or NOT_CLEARED");
        }
        this.decision      = decision;
        this.decisionNote  = note == null || note.isBlank() ? null : note.trim();
        this.decidedBy     = by;
        this.decidedByName = byName;
        this.decidedAt     = Instant.now();
        this.updatedAt     = Instant.now();
    }

    // ── Queries ────────────────────────────────────────────────────────────────

    public boolean isPending() { return result == ScreeningResult.PENDING; }
    public boolean isFailed()  { return result == ScreeningResult.FAIL || "NOT_CLEARED".equals(decision); }
    public boolean isPassed()  { return result == ScreeningResult.PASS; }

    // ── Enums ──────────────────────────────────────────────────────────────────

    public enum ScreeningType {
        POLYGRAPH, CRIMINAL_RECORD_CHECK, REFERENCE_CHECK,
        DRUG_TEST, PSYCHOMETRIC, CREDIT_CHECK,
        ID_VERIFICATION, QUALIFICATION_VERIFICATION, OTHER
    }

    public enum ScreeningReason {
        ONBOARDING, PERIODIC, POST_INCIDENT, RANDOM, CLIENT_REQUESTED
    }

    public enum ScreeningResult {
        PASS, FAIL, INCONCLUSIVE, PENDING
    }
}