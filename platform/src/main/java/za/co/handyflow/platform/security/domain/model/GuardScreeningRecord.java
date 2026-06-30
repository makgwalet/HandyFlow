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
        this.updatedAt   = Instant.now();
    }

    // ── Queries ────────────────────────────────────────────────────────────────

    public boolean isPending() { return result == ScreeningResult.PENDING; }
    public boolean isFailed()  { return result == ScreeningResult.FAIL; }
    public boolean isPassed()  { return result == ScreeningResult.PASS; }

    // ── Enums ──────────────────────────────────────────────────────────────────

    public enum ScreeningType {
        POLYGRAPH, CRIMINAL_RECORD_CHECK, REFERENCE_CHECK,
        DRUG_TEST, PSYCHOMETRIC, CREDIT_CHECK, OTHER
    }

    public enum ScreeningReason {
        ONBOARDING, PERIODIC, POST_INCIDENT, RANDOM, CLIENT_REQUESTED
    }

    public enum ScreeningResult {
        PASS, FAIL, INCONCLUSIVE, PENDING
    }
}