package za.co.handyflow.platform.debtcollection.domain.model;

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

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * A debtor's dispute of a case or of one invoice on it (finance F16). Values are strings validated by
 * DisputeRules. Opening one pauses the case; the status the case had is remembered so resolving can restore it.
 */
@Entity
@Table(name = "debtcollection_disputes")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Dispute extends AggregateRoot<Dispute> {

    @Column(name = "case_id", nullable = false)
    private UUID caseId;

    @Column(name = "invoice_id")
    private UUID invoiceId;

    @Column(name = "reason_type", nullable = false, length = 25)
    private String reasonType;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String description;

    @Column(name = "amount_disputed", precision = 15, scale = 2)
    private BigDecimal amountDisputed;

    @Column(nullable = false, length = 15)
    private String status;

    @Column(name = "raised_on", nullable = false)
    private LocalDate raisedOn;

    @Column(name = "response_due", nullable = false)
    private LocalDate responseDue;

    @Column(name = "owner_user_id")
    private UUID ownerUserId;

    @Column(name = "owner_user_name", length = 255)
    private String ownerUserName;

    @Column(length = 15)
    private String resolution;

    @Column(name = "amount_conceded", precision = 15, scale = 2)
    private BigDecimal amountConceded;

    @Column(name = "resolution_note", columnDefinition = "TEXT")
    private String resolutionNote;

    @Column(name = "resolved_by")
    private UUID resolvedBy;

    @Column(name = "resolved_by_name", length = 255)
    private String resolvedByName;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    @Column(name = "credit_note_ref", length = 60)
    private String creditNoteRef;

    @Enumerated(EnumType.STRING)
    @Column(name = "case_status_before", length = 25)
    private CaseStatus caseStatusBefore;

    @Column(name = "created_by")
    private UUID createdBy;

    public static Dispute open(TenantId tenantId, UUID caseId, UUID invoiceId, String reasonType, String description,
                               BigDecimal amountDisputed, LocalDate raisedOn, LocalDate responseDue,
                               UUID ownerId, String ownerName, CaseStatus caseStatusBefore, UUID createdBy) {
        Dispute d = new Dispute();
        d.initTenantId(tenantId);
        d.caseId = caseId;
        d.invoiceId = invoiceId;
        d.reasonType = reasonType;
        d.description = description.trim();
        d.amountDisputed = amountDisputed;
        d.status = "OPEN";
        d.raisedOn = raisedOn;
        d.responseDue = responseDue;
        d.ownerUserId = ownerId;
        d.ownerUserName = ownerName;
        d.caseStatusBefore = caseStatusBefore;
        d.createdBy = createdBy;
        return d;
    }

    public void takeOn(UUID ownerId, String ownerName) {
        this.status = "UNDER_REVIEW";
        this.ownerUserId = ownerId;
        this.ownerUserName = ownerName;
    }

    public void reassign(UUID ownerId, String ownerName) {
        this.ownerUserId = ownerId;
        this.ownerUserName = ownerName;
    }

    public void resolve(String resolution, String note, BigDecimal conceded, String creditNoteRef, UUID by, String byName) {
        this.status = "RESOLVED";
        this.resolution = resolution;
        this.resolutionNote = note.trim();
        this.amountConceded = conceded == null || conceded.signum() == 0 ? null : conceded;
        this.creditNoteRef = creditNoteRef == null || creditNoteRef.isBlank() ? null : creditNoteRef.trim();
        this.resolvedBy = by;
        this.resolvedByName = byName;
        this.resolvedAt = Instant.now();
    }
}
