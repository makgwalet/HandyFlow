package za.co.handyflow.platform.recruitmentagency.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * An invoice the agency sends to a client for one confirmed placement.
 * Same status-lifecycle shape as PayFeeNote (DRAFT -> SENT ->
 * PARTIAL/PAID) — that pattern is proven and worth reusing. Unlike
 * PayFeeNote, this doesn't need a separate line-item table: a
 * recruitment placement invoice is overwhelmingly one line (the
 * placement fee itself), described directly on this entity rather than
 * forcing a multi-line structure the domain doesn't actually need.
 * <p>
 * If a genuine second line ever becomes necessary (e.g. a partial
 * guarantee-period refund credited against a later invoice), that's
 * real, separate follow-up work — not pre-built here speculatively.
 */
@Entity
@Table(name = "reca_invoices")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RecAgencyInvoice {

    @Id
    private UUID id = UUID.randomUUID();

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "client_id", nullable = false)
    private UUID clientId;

    @Column(name = "placement_id", nullable = false, unique = true)
    private UUID placementId; // one invoice per placement — enforced by the unique constraint, not just convention

    @Column(name = "invoice_number", nullable = false)
    private String invoiceNumber;

    @Column(name = "description", nullable = false)
    private String description; // e.g. "Placement fee — [Candidate] for [Requisition]"

    @Column(name = "invoice_date", nullable = false)
    private LocalDate invoiceDate;

    @Column(name = "due_date", nullable = false)
    private LocalDate dueDate;

    @Column(name = "subtotal", nullable = false, precision = 15, scale = 2)
    private BigDecimal subtotal;

    @Column(name = "vat_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal vatAmount;

    @Column(name = "total", nullable = false, precision = 15, scale = 2)
    private BigDecimal total;

    @Column(name = "amount_paid", nullable = false, precision = 15, scale = 2)
    private BigDecimal amountPaid = BigDecimal.ZERO;

    @Column(name = "status", nullable = false)
    private String status = "DRAFT"; // DRAFT | SENT | PARTIAL | PAID | OVERDUE

    @Column(name = "sent_at")
    private Instant sentAt;

    @Column(name = "paid_at")
    private Instant paidAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "credit_note_required", nullable = false)
    private boolean creditNoteRequired = false;

    @Column(name = "credit_note_reason", columnDefinition = "TEXT")
    private String creditNoteReason;

    public static RecAgencyInvoice create(UUID tenantId, UUID clientId, UUID placementId,
                                          String invoiceNumber, String description,
                                          LocalDate invoiceDate, LocalDate dueDate,
                                          BigDecimal subtotal, BigDecimal vatAmount) {
        RecAgencyInvoice inv = new RecAgencyInvoice();
        inv.tenantId = tenantId;
        inv.clientId = clientId;
        inv.placementId = placementId;
        inv.invoiceNumber = invoiceNumber;
        inv.description = description;
        inv.invoiceDate = invoiceDate;
        inv.dueDate = dueDate;
        inv.subtotal = subtotal;
        inv.vatAmount = vatAmount;
        inv.total = subtotal.add(vatAmount);
        inv.status = "DRAFT";
        inv.createdAt = Instant.now();
        return inv;
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

    public void flagForCreditNote(String reason) {
        this.creditNoteRequired = true;
        this.creditNoteReason = reason;
    }
}