package za.co.handyflow.platform.collectionsagency.domain.model;

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
 * The agency's invoice to a creditor client for commission earned on a
 * remittance — the ONLY thing in this module that posts to the tenant's
 * real chart of accounts (see CollAgencyTrustTransactionService.
 * processRemittance()). Same status-lifecycle shape as RecAgencyInvoice/
 * BookAgencyInvoice (DRAFT -> SENT -> PARTIAL/PAID) — proven pattern,
 * reused directly rather than inventing a new one.
 * <p>
 * ONE INVOICE PER REMITTANCE: created and immediately issued (revenue
 * journal posted — Dr AR, Cr Revenue, Cr VAT Output, same shape
 * RecruitmentAgencyService.postInvoiceRevenueJournal() already proves)
 * at the moment a remittance is processed, describing the commission on
 * that specific remittance run.
 * <p>
 * FLAGGED, NOT GUESSED: recordPayment()/markPaid() below does NOT itself
 * post a second "payment received" GL journal (Dr Bank, Cr AR) the way
 * RecruitmentAgencyService.postPaymentJournal() does for its own
 * invoices. In practice, agency commission is very often settled by
 * simply being netted off the amount remitted to the client, not by a
 * separate bank transfer — and whether/how that netting should itself
 * appear on the GL (e.g. via a trust-liability clearing account) is a
 * real accounting-policy question this session should not guess at on
 * revenue-critical code. If commission is genuinely settled by a
 * separate bank transfer, staff can post that payment journal manually
 * via the normal Accounting journal-entry screen, same as any other
 * manually-reconciled AR item — this entity's own recordPayment() just
 * updates amountPaid/status for internal tracking of "has this
 * commission invoice been settled," without assuming a specific GL
 * treatment.
 */
@Entity
@Table(name = "collagency_commission_invoices")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CollAgencyCommissionInvoice {

    @Id
    private UUID id = UUID.randomUUID();

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "client_id", nullable = false)
    private UUID clientId;

    @Column(name = "invoice_number", nullable = false)
    private String invoiceNumber;

    @Column(name = "description", nullable = false)
    private String description;

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
    private String status = "DRAFT"; // DRAFT | SENT | PARTIAL | PAID

    @Column(name = "sent_at")
    private Instant sentAt;

    @Column(name = "paid_at")
    private Instant paidAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public static CollAgencyCommissionInvoice create(UUID tenantId, UUID clientId, String invoiceNumber,
                                                      String description, LocalDate invoiceDate, LocalDate dueDate,
                                                      BigDecimal subtotal, BigDecimal vatAmount) {
        CollAgencyCommissionInvoice inv = new CollAgencyCommissionInvoice();
        inv.tenantId = tenantId;
        inv.clientId = clientId;
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
}
