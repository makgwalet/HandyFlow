package za.co.handyflow.platform.legalcompliance.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.AggregateRoot;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * A single compliance obligation the business must meet on a recurring or
 * one-off basis (POPIA registration renewal, annual CIPC return, OHS Act
 * safety-file review, an industry-specific licence review, ...).
 * <p>
 * status is a computed field, kept in sync by refreshStatus() rather than
 * derived on every read — same "materialize the state, don't recompute it
 * live everywhere" choice already made for Contract.status and
 * ObligationStatus's own class Javadoc explains why NON_COMPLIANT is the
 * one value refreshStatus() never overwrites.
 */
@Entity
@Table(name = "legalcompliance_regulatory_obligations")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RegulatoryObligation extends AggregateRoot<RegulatoryObligation> {

    @Column(nullable = false, length = 255)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ObligationCategory category;

    /** Free-text statute/regulation name — required detail when category is INDUSTRY_SPECIFIC or OTHER, optional otherwise. */
    @Column(name = "regulation_reference", length = 255)
    private String regulationReference;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "responsible_user_id")
    private UUID responsibleUserId;

    /** Denormalized display name — same convention as every *ByName field elsewhere in this codebase (see ScmController's own rationale). */
    @Column(name = "responsible_user_name", length = 255)
    private String responsibleUserName;

    @Column(name = "review_date", nullable = false)
    private LocalDate reviewDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RecurrenceInterval recurrence;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ObligationStatus status;

    /** Optional link to a specific contracting.Contract (via ContractingFacade) this obligation stems from — e.g. a supplier contract's compliance clause. */
    @Column(name = "linked_contract_id")
    private UUID linkedContractId;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Column(name = "last_reviewed_at")
    private Instant lastReviewedAt;

    @Column(name = "last_reviewed_by")
    private UUID lastReviewedBy;

    @Column(name = "last_reviewed_by_name", length = 255)
    private String lastReviewedByName;

    @Column(name = "created_by")
    private UUID createdBy;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Column(name = "deleted_by")
    private UUID deletedBy;

    public static RegulatoryObligation create(TenantId tenantId, String title, ObligationCategory category,
                                              String regulationReference, String description,
                                              UUID responsibleUserId, String responsibleUserName,
                                              LocalDate reviewDate, RecurrenceInterval recurrence,
                                              UUID createdBy) {
        RegulatoryObligation o = new RegulatoryObligation();
        o.initTenantId(tenantId);
        o.title = title;
        o.category = category;
        o.regulationReference = regulationReference;
        o.description = description;
        o.responsibleUserId = responsibleUserId;
        o.responsibleUserName = responsibleUserName;
        o.reviewDate = reviewDate;
        o.recurrence = recurrence;
        o.status = ObligationStatus.COMPLIANT;
        o.createdBy = createdBy;
        return o;
    }

    public void update(String title, String regulationReference, String description,
                       UUID responsibleUserId, String responsibleUserName,
                       LocalDate reviewDate, RecurrenceInterval recurrence) {
        this.title = title;
        this.regulationReference = regulationReference;
        this.description = description;
        this.responsibleUserId = responsibleUserId;
        this.responsibleUserName = responsibleUserName;
        this.reviewDate = reviewDate;
        this.recurrence = recurrence;
    }

    public void linkContract(UUID contractId) {
        this.linkedContractId = contractId;
    }

    /**
     * Marks this obligation reviewed today and, unless it is a ONCE
     * obligation, rolls reviewDate forward by one recurrence interval —
     * same "advance from today, not from the old due date" choice as
     * every recurring-schedule generator elsewhere in this codebase, so a
     * late review doesn't compound into an ever-earlier due date.
     */
    public void markReviewed(UUID reviewedBy, String reviewedByName, String notes) {
        this.lastReviewedAt = Instant.now();
        this.lastReviewedBy = reviewedBy;
        this.lastReviewedByName = reviewedByName;
        if (notes != null && !notes.isBlank()) {
            this.notes = notes;
        }
        this.status = ObligationStatus.COMPLIANT;
        this.reviewDate = switch (recurrence) {
            case ONCE -> this.reviewDate;
            case MONTHLY -> LocalDate.now().plusMonths(1);
            case QUARTERLY -> LocalDate.now().plusMonths(3);
            case ANNUALLY -> LocalDate.now().plusYears(1);
        };
    }

    /** A human determination that the business is NOT currently meeting this obligation — never set by the scheduler, only by a person recording a real finding. */
    public void markNonCompliant(String notes) {
        this.status = ObligationStatus.NON_COMPLIANT;
        if (notes != null && !notes.isBlank()) {
            this.notes = notes;
        }
    }

    /**
     * Recomputes COMPLIANT/DUE_SOON/OVERDUE from reviewDate — called by
     * the scheduler on every run. Deliberately leaves NON_COMPLIANT alone:
     * that status only changes via an explicit markReviewed()/
     * markNonCompliant() call from a person, never silently cleared by a
     * date crossing a threshold.
     */
    public void refreshStatus(LocalDate today, int dueSoonThresholdDays) {
        if (status == ObligationStatus.NON_COMPLIANT) return;
        if (reviewDate.isBefore(today)) {
            status = ObligationStatus.OVERDUE;
        } else if (!reviewDate.isAfter(today.plusDays(dueSoonThresholdDays))) {
            status = ObligationStatus.DUE_SOON;
        } else {
            status = ObligationStatus.COMPLIANT;
        }
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }

    public void softDelete(UUID deletedBy) {
        this.deletedAt = Instant.now();
        this.deletedBy = deletedBy;
    }
}
