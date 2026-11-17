package za.co.handyflow.platform.supplychain.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * A proposed change to an approved purchase order. The PO itself is untouched until this
 * is APPLIED — so an approved amount can never be changed behind the approver's back, and
 * receiving can carry on against the current revision while an amendment awaits approval.
 */
@Entity
@Table(name = "sc_po_amendments")
@Getter
@NoArgsConstructor
public class ScPoAmendment {

    public static final String PENDING_APPROVAL = "PENDING_APPROVAL";
    public static final String APPLIED = "APPLIED";
    public static final String REJECTED = "REJECTED";
    public static final String WITHDRAWN = "WITHDRAWN";

    @Id UUID id;
    @Column(name = "tenant_id", nullable = false) UUID tenantId;
    @Column(name = "purchase_order_id", nullable = false) UUID purchaseOrderId;
    @Column(name = "amendment_number", nullable = false) int amendmentNumber;
    @Column(name = "base_revision", nullable = false) int baseRevision;
    @Column(nullable = false, length = 20) String status;
    @Column(nullable = false) boolean material;
    @Column(nullable = false, length = 500) String reason;
    @Column(name = "change_summary", length = 1000) String changeSummary;

    // Header changes — null means "unchanged".
    @Column(name = "new_required_by_date") LocalDate newRequiredByDate;
    @Column(name = "new_deliver_to_location") UUID newDeliverToLocation;
    @Column(name = "new_notes", columnDefinition = "TEXT") String newNotes;
    @Column(name = "new_terms", columnDefinition = "TEXT") String newTerms;

    @Column(name = "previous_total", nullable = false, precision = 15, scale = 2) BigDecimal previousTotal;
    @Column(name = "proposed_total", nullable = false, precision = 15, scale = 2) BigDecimal proposedTotal;

    @Column(name = "requested_by") UUID requestedBy;
    @Column(name = "requested_by_name", length = 255) String requestedByName;
    @Column(name = "decided_by") UUID decidedBy;
    @Column(name = "decided_by_name", length = 255) String decidedByName;
    @Column(name = "decided_at") Instant decidedAt;
    @Column(name = "decision_note", length = 500) String decisionNote;
    @Column(name = "created_at", nullable = false) Instant createdAt;
    @Column(name = "updated_at", nullable = false) Instant updatedAt;

    public static ScPoAmendment create(UUID tenantId, UUID poId, int amendmentNumber, int baseRevision,
                                       boolean material, String reason, String changeSummary,
                                       LocalDate newRequiredByDate, UUID newDeliverToLocation,
                                       String newNotes, String newTerms,
                                       BigDecimal previousTotal, BigDecimal proposedTotal,
                                       UUID requestedBy, String requestedByName) {
        ScPoAmendment a = new ScPoAmendment();
        a.id = UUID.randomUUID();
        a.tenantId = tenantId;
        a.purchaseOrderId = poId;
        a.amendmentNumber = amendmentNumber;
        a.baseRevision = baseRevision;
        a.status = PENDING_APPROVAL;
        a.material = material;
        a.reason = reason.length() > 500 ? reason.substring(0, 500) : reason;
        a.changeSummary = changeSummary != null && changeSummary.length() > 1000
                ? changeSummary.substring(0, 1000) : changeSummary;
        a.newRequiredByDate = newRequiredByDate;
        a.newDeliverToLocation = newDeliverToLocation;
        a.newNotes = newNotes;
        a.newTerms = newTerms;
        a.previousTotal = previousTotal;
        a.proposedTotal = proposedTotal;
        a.requestedBy = requestedBy;
        a.requestedByName = requestedByName;
        a.createdAt = Instant.now();
        a.updatedAt = Instant.now();
        return a;
    }

    public boolean isPending() { return PENDING_APPROVAL.equals(status); }

    public void markApplied(UUID by, String byName, String note) {
        requirePending();
        status = APPLIED;
        decide(by, byName, note);
    }

    public void markRejected(UUID by, String byName, String note) {
        requirePending();
        status = REJECTED;
        decide(by, byName, note);
    }

    public void markWithdrawn(UUID by, String byName, String note) {
        requirePending();
        status = WITHDRAWN;
        decide(by, byName, note);
    }

    private void requirePending() {
        if (!isPending()) throw new IllegalStateException("Amendment is already " + status);
    }

    private void decide(UUID by, String byName, String note) {
        decidedBy = by;
        decidedByName = byName;
        decidedAt = Instant.now();
        decisionNote = note != null && note.length() > 500 ? note.substring(0, 500) : note;
        updatedAt = Instant.now();
    }
}
