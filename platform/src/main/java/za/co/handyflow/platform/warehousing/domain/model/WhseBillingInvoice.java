package za.co.handyflow.platform.warehousing.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * The operator's invoice to a client for storage + handling fees over a
 * billing period — the ONLY thing in this module that posts to the
 * tenant's real chart of accounts (see WhseBillingService). Same status-
 * lifecycle shape as every other provider module's own client invoice
 * (RecAgencyInvoice, CollAgencyCommissionInvoice): DRAFT -&gt; SENT -&gt;
 * PARTIAL/PAID.
 * <p>
 * storageFee and handlingFee are kept as separate columns (not just a
 * combined subtotal) so a client-facing statement can show the two cost
 * drivers apart — this was flagged as worth doing during design since a
 * 3PL client's most common question is "why did my bill go up," and
 * "more stock sitting in storage" vs. "more order volume" are different
 * answers.
 */
@Entity
@Table(name = "whse_billing_invoices")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WhseBillingInvoice {

    @Id
    private UUID id = UUID.randomUUID();

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "client_id", nullable = false)
    private UUID clientId;

    @Column(name = "invoice_number", nullable = false)
    private String invoiceNumber;

    @Column(name = "period_start", nullable = false)
    private LocalDate periodStart;

    @Column(name = "period_end", nullable = false)
    private LocalDate periodEnd;

    @Column(name = "invoice_date", nullable = false)
    private LocalDate invoiceDate;

    @Column(name = "due_date", nullable = false)
    private LocalDate dueDate;

    @Column(name = "storage_fee", nullable = false, precision = 15, scale = 2)
    private BigDecimal storageFee;

    @Column(name = "handling_fee", nullable = false, precision = 15, scale = 2)
    private BigDecimal handlingFee;

    @Column(name = "vat_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal vatAmount;

    @Column(name = "subtotal", nullable = false, precision = 15, scale = 2)
    private BigDecimal subtotal;

    @Column(name = "total", nullable = false, precision = 15, scale = 2)
    private BigDecimal total;

    @Column(name = "amount_paid", nullable = false, precision = 15, scale = 2)
    private BigDecimal amountPaid = BigDecimal.ZERO;

    @Column(name = "status", nullable = false)
    private String status = "DRAFT"; // DRAFT (awaiting approval) | SENT | PARTIAL | PAID | REJECTED

    @Column(name = "sent_at")
    private Instant sentAt;

    @Column(name = "paid_at")
    private Instant paidAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "prepared_by")
    private UUID preparedBy;

    @Column(name = "decided_by")
    private UUID decidedBy;

    @Column(name = "decided_at")
    private Instant decidedAt;

    @Column(name = "decision_note")
    private String decisionNote;

    @Column(name = "storage_minimum_topup", nullable = false)
    private BigDecimal storageMinimumTopup = BigDecimal.ZERO;

    @Column(name = "storage_location_fees", nullable = false)
    private BigDecimal storageLocationFees = BigDecimal.ZERO;

    @Column(name = "sole_exception", nullable = false)
    private boolean soleException;

    public static WhseBillingInvoice create(UUID tenantId, UUID clientId, String invoiceNumber,
                                             LocalDate periodStart, LocalDate periodEnd, LocalDate invoiceDate,
                                             LocalDate dueDate, BigDecimal storageFee, BigDecimal handlingFee,
                                             BigDecimal vatAmount) {
        WhseBillingInvoice inv = new WhseBillingInvoice();
        inv.tenantId = tenantId;
        inv.clientId = clientId;
        inv.invoiceNumber = invoiceNumber;
        inv.periodStart = periodStart;
        inv.periodEnd = periodEnd;
        inv.invoiceDate = invoiceDate;
        inv.dueDate = dueDate;
        inv.storageFee = storageFee;
        inv.handlingFee = handlingFee;
        inv.vatAmount = vatAmount;
        inv.subtotal = storageFee.add(handlingFee);
        inv.total = inv.subtotal.add(vatAmount);
        inv.status = "DRAFT";
        inv.createdAt = Instant.now();
        return inv;
    }

    /** Part of the storage fee that is the contract minimum rather than stored goods. */
    public void recordMinimumTopUp(BigDecimal amount) {
        this.storageMinimumTopup = amount == null ? BigDecimal.ZERO : amount;
    }

    /** Part of the storage fee that is the fixed pallet/bin fees. */
    public void recordLocationFees(BigDecimal amount) {
        this.storageLocationFees = amount == null ? BigDecimal.ZERO : amount;
    }

    public void preparedBy(UUID userId) {
        this.preparedBy = userId;
    }

    /** A second person approves a DRAFT invoice; it is then issued. {@code sole} marks the audited one-person exception. */
    public void approve(UUID approver, String note, boolean sole) {
        if (!"DRAFT".equals(status)) throw new IllegalStateException("Only DRAFT invoices can be approved");
        this.decidedBy = approver;
        this.decidedAt = Instant.now();
        this.decisionNote = note;
        this.soleException = sole;
        markSent();
    }

    public void reject(UUID by, String note) {
        if (!"DRAFT".equals(status)) throw new IllegalStateException("Only DRAFT invoices can be rejected");
        this.status = "REJECTED";
        this.decidedBy = by;
        this.decidedAt = Instant.now();
        this.decisionNote = note;
    }

    public void markSent() {
        if (!"DRAFT".equals(status)) throw new IllegalStateException("Only DRAFT invoices can be sent");
        this.status = "SENT";
        this.sentAt = Instant.now();
    }

    public void recordPayment(BigDecimal amount) {
        this.amountPaid = this.amountPaid.add(amount);
        if (this.amountPaid.compareTo(this.total) >= 0) {
            this.status = "PAID";
            this.paidAt = Instant.now();
        } else if (this.amountPaid.compareTo(BigDecimal.ZERO) > 0) {
            this.status = "PARTIAL";
        }
    }

    public BigDecimal balance() {
        return total.subtract(amountPaid);
    }
}
