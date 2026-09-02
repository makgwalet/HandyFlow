package za.co.handyflow.platform.debtcollection.domain.model;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.AggregateRoot;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * A formal, staff-managed internal debt collection case against one debtor
 * (a CRM customer, or a walk-in debtor identified only by name/contact
 * snapshot — mirrors Invoice.customerId's own nullability). Opened once
 * invoicing's soft automatic reminders and accounting's AR-aging alerts
 * have not resolved the debt; this aggregate is the record of the human
 * collection effort from that point on: contact history (via
 * CollectionContactLog), an optional structured PaymentPlan, and a path to
 * SETTLED, WRITTEN_OFF, or HANDED_TO_LEGAL.
 * <p>
 * Scoped to the debtor relationship, not to a single invoice — a case can
 * cover several overdue invoices for the same debtor at once (linkedInvoiceIds).
 * totalOutstanding is a snapshot refreshed by the service layer against
 * InvoicingFacade, not a live-computed value — same "materialize, don't
 * recompute everywhere" choice RegulatoryObligation.status already made.
 */
@Entity
@Table(name = "debtcollection_cases")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DebtCollectionCase extends AggregateRoot<DebtCollectionCase> {

    @Column(name = "case_number", nullable = false, length = 30)
    private String caseNumber;

    /** Nullable — mirrors Invoice.customerId; a walk-in debtor has no CRM record. */
    @Column(name = "customer_id")
    private UUID customerId;

    /** Snapshot at case-open time, so the case is legible even if the CRM record later changes or the debtor was never a CRM customer. */
    @Column(name = "debtor_name", nullable = false, length = 255)
    private String debtorName;

    @Column(name = "debtor_email", length = 255)
    private String debtorEmail;

    @Column(name = "debtor_phone", length = 50)
    private String debtorPhone;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 25)
    private CaseStatus status;

    /** Snapshot, refreshed by the service layer against InvoicingFacade.findOutstandingInvoices() — not recomputed live on every read. */
    @Column(name = "total_outstanding", nullable = false, precision = 15, scale = 2)
    private BigDecimal totalOutstanding;

    /** Invoice ids (owned by `invoicing`) this case covers. This module never copies invoice line data — id references only. */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "debtcollection_case_invoices", joinColumns = @JoinColumn(name = "case_id"))
    @Column(name = "invoice_id", nullable = false)
    private Set<UUID> linkedInvoiceIds = new HashSet<>();

    @Column(name = "opened_date", nullable = false)
    private LocalDate openedDate;

    @Column(name = "closed_date")
    private LocalDate closedDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "closure_reason", length = 25)
    private ClosureReason closureReason;

    @Column(name = "assigned_to_user_id")
    private UUID assignedToUserId;

    @Column(name = "assigned_to_user_name", length = 255)
    private String assignedToUserName;

    /** Optional link to a contracting.Contract (an Acknowledgment of Debt, typically) — id only, see package-info for why. */
    @Column(name = "linked_contract_id")
    private UUID linkedContractId;

    @Column(name = "last_contact_date")
    private LocalDate lastContactDate;

    /** Next follow-up/action due date — surfaced by the notification scheduler. */
    @Column(name = "next_action_date")
    private LocalDate nextActionDate;

    @Column(name = "write_off_amount", precision = 15, scale = 2)
    private BigDecimal writeOffAmount;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Column(name = "created_by")
    private UUID createdBy;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Column(name = "deleted_by")
    private UUID deletedBy;

    public static DebtCollectionCase open(TenantId tenantId, String caseNumber, UUID customerId,
                                           String debtorName, String debtorEmail, String debtorPhone,
                                           BigDecimal totalOutstanding, Set<UUID> linkedInvoiceIds,
                                           LocalDate openedDate, UUID assignedToUserId, String assignedToUserName,
                                           String notes, UUID createdBy) {
        if (debtorName == null || debtorName.isBlank()) {
            throw new IllegalArgumentException("debtorName is required");
        }
        if (totalOutstanding == null || totalOutstanding.signum() <= 0) {
            throw new IllegalArgumentException("totalOutstanding must be positive");
        }
        DebtCollectionCase c = new DebtCollectionCase();
        c.initTenantId(tenantId);
        c.caseNumber = caseNumber;
        c.customerId = customerId;
        c.debtorName = debtorName;
        c.debtorEmail = debtorEmail;
        c.debtorPhone = debtorPhone;
        c.status = CaseStatus.OPEN;
        c.totalOutstanding = totalOutstanding;
        c.linkedInvoiceIds = linkedInvoiceIds != null ? new HashSet<>(linkedInvoiceIds) : new HashSet<>();
        c.openedDate = openedDate != null ? openedDate : LocalDate.now();
        c.assignedToUserId = assignedToUserId;
        c.assignedToUserName = assignedToUserName;
        c.notes = notes;
        c.createdBy = createdBy;
        return c;
    }

    public void refreshTotalOutstanding(BigDecimal totalOutstanding) {
        if (totalOutstanding == null || totalOutstanding.signum() < 0) {
            throw new IllegalArgumentException("totalOutstanding cannot be negative");
        }
        this.totalOutstanding = totalOutstanding;
    }

    public void linkInvoice(UUID invoiceId) {
        this.linkedInvoiceIds.add(invoiceId);
    }

    public void unlinkInvoice(UUID invoiceId) {
        this.linkedInvoiceIds.remove(invoiceId);
    }

    public void assign(UUID userId, String userName) {
        this.assignedToUserId = userId;
        this.assignedToUserName = userName;
    }

    public void linkContract(UUID contractId) {
        this.linkedContractId = contractId;
    }

    public void scheduleNextAction(LocalDate date) {
        this.nextActionDate = date;
    }

    /** Called by the service layer whenever a CollectionContactLog entry is recorded, so the case's own "last contact" is always in sync with its log. */
    public void recordContact(LocalDate contactDate) {
        if (this.lastContactDate == null || contactDate.isAfter(this.lastContactDate)) {
            this.lastContactDate = contactDate;
        }
    }

    public void updateNotes(String notes) {
        this.notes = notes;
    }

    /**
     * Moves the case to any non-terminal workflow status. CLOSED is
     * deliberately excluded here — only close() may set it, so closing
     * always records a ClosureReason and closedDate together.
     */
    public void advanceStatus(CaseStatus newStatus) {
        if (this.status == CaseStatus.CLOSED) {
            throw new IllegalStateException("Cannot change status of a closed case");
        }
        if (newStatus == CaseStatus.CLOSED) {
            throw new IllegalArgumentException("Use close() to close a case, not advanceStatus()");
        }
        this.status = newStatus;
    }

    /**
     * Formal write-off — a financial determination the debt will not be
     * recovered. Moves status to WRITTEN_OFF directly (does not require
     * close() to also be called; a written-off case may still be
     * reported on before being closed out administratively).
     */
    public void writeOff(BigDecimal amount, String reason) {
        if (this.status == CaseStatus.CLOSED) {
            throw new IllegalStateException("Cannot write off a closed case");
        }
        if (amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException("writeOffAmount must be positive");
        }
        this.status = CaseStatus.WRITTEN_OFF;
        this.writeOffAmount = amount;
        if (reason != null && !reason.isBlank()) {
            this.notes = (this.notes == null ? "" : this.notes + "\n") + "Write-off: " + reason;
        }
    }

    /** Terminal action — records why and when. */
    public void close(ClosureReason reason, String outcomeNotes) {
        if (this.status == CaseStatus.CLOSED) {
            throw new IllegalStateException("Case is already closed");
        }
        if (reason == null) {
            throw new IllegalArgumentException("closureReason is required");
        }
        this.status = CaseStatus.CLOSED;
        this.closureReason = reason;
        this.closedDate = LocalDate.now();
        if (outcomeNotes != null && !outcomeNotes.isBlank()) {
            this.notes = (this.notes == null ? "" : this.notes + "\n") + outcomeNotes;
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
