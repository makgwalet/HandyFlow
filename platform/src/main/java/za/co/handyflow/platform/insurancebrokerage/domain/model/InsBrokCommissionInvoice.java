package za.co.handyflow.platform.insurancebrokerage.domain.model;

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
 * The brokerage's OWN earned-commission record on a bound/renewed policy
 * — the ONLY thing in this module that posts to the tenant's real chart
 * of accounts (see {@code InsBrokCommissionInvoiceService.issueForPolicy()}).
 * Deliberately mirrors {@code CollAgencyCommissionInvoice} field-for-field
 * and lifecycle-for-lifecycle (DRAFT -&gt; SENT -&gt; PARTIAL/PAID) —
 * confirmed as the correct precedent to copy, not invent a new shape.
 * <p>
 * ONE INVOICE PER POLICY ACTIVATION: created and immediately issued
 * (revenue journal posted — Dr AR, Cr Revenue, Cr VAT Output, same shape
 * {@code CollAgencyTrustTransactionService.postCommissionRevenueJournal()}
 * already proves) the instant a policy transitions to ACTIVE — new
 * business (via bind -&gt; activate) or a renewal (which lands directly
 * in ACTIVE). Commission base is that policy term's {@code premiumAmount}
 * — a simplification flagged, not silently assumed: real brokerage
 * commission structures often distinguish first-year vs. renewal rates,
 * or apply to annualised rather than per-instalment premium; this MVP
 * applies one rate to the term's captured {@code premiumAmount} as
 * entered, same "policy lifecycle + commission only" scope confirmed
 * before this increment was built.
 * <p>
 * FLAGGED, NOT GUESSED (same as {@code CollAgencyCommissionInvoice}):
 * {@code recordPayment()} does NOT itself post a second "payment
 * received" GL journal (Dr Bank, Cr AR). Whether/how commission
 * settlement should hit the GL (direct bank transfer vs. netted off a
 * future remittance-style arrangement) is a real accounting-policy
 * question this module does not guess at on revenue-critical code — see
 * this same note on {@code CollAgencyCommissionInvoice} for the full
 * rationale, reused verbatim because the situation is identical.
 */
@Entity
@Table(name = "insbrok_commission_invoices")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class InsBrokCommissionInvoice {

    @Id
    private UUID id = UUID.randomUUID();

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "client_id", nullable = false)
    private UUID clientId;

    @Column(name = "policy_id", nullable = false)
    private UUID policyId;

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

    public static InsBrokCommissionInvoice create(UUID tenantId, UUID clientId, UUID policyId, String invoiceNumber,
                                                    String description, LocalDate invoiceDate, LocalDate dueDate,
                                                    BigDecimal subtotal, BigDecimal vatAmount) {
        InsBrokCommissionInvoice inv = new InsBrokCommissionInvoice();
        inv.tenantId = tenantId;
        inv.clientId = clientId;
        inv.policyId = policyId;
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
        if (!"DRAFT".equals(status)) {
            throw new IllegalStateException("Only a DRAFT commission invoice can be sent (current status: " + status + ")");
        }
        this.status = "SENT";
        this.sentAt = Instant.now();
    }

    public void recordPayment(BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException("Payment amount must be positive");
        }
        this.amountPaid = this.amountPaid.add(amount);
        if (this.amountPaid.compareTo(this.total) >= 0) {
            this.status = "PAID";
            this.paidAt = Instant.now();
        } else {
            this.status = "PARTIAL";
        }
    }

    public BigDecimal balance() {
        return total.subtract(amountPaid);
    }
}
