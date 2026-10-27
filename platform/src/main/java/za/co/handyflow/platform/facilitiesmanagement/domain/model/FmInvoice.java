package za.co.handyflow.platform.facilitiesmanagement.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * A flat-entity invoice (single subtotal / VAT / total) generated per client per billing period.
 * <p>
 * Chunk 7 workflow — preparation and approval are separate steps:
 * <pre>
 *   DRAFT --approve--> APPROVED --send--> SENT --payment--> PARTIAL --payment--> PAID
 *   DRAFT --cancel--> CANCELLED
 * </pre>
 * The invoice reaches the ledger when it is APPROVED (not when it is drafted), so an unapproved draft never
 * affects the books. {@code glStatus} tracks that posting: NOT_POSTED (draft) -> PENDING (approved) ->
 * POSTED, or FAILED and retried automatically. LEGACY marks invoices created under the old flow, which
 * posted at generation; those are never re-posted.
 * <p>
 * A snapshot of the VAT rate used is kept on the invoice so later rate changes never alter it.
 */
@Entity
@Table(name = "fm_invoices")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FmInvoice {

    @Id
    private UUID id = UUID.randomUUID();

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;

    @Column(name = "client_id", nullable = false)
    private UUID clientId;

    @Column(name = "invoice_number", nullable = false, unique = true)
    private String invoiceNumber;

    @Column(name = "period_start", nullable = false)
    private LocalDate periodStart;

    @Column(name = "period_end", nullable = false)
    private LocalDate periodEnd;

    @Column(name = "issue_date", nullable = false)
    private LocalDate issueDate;

    @Column(name = "due_date", nullable = false)
    private LocalDate dueDate;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal subtotal;

    @Column(name = "vat_rate", nullable = false, precision = 5, scale = 2)
    private BigDecimal vatRate = BigDecimal.ZERO;

    @Column(name = "vat_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal vatAmount;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal total;

    @Column(name = "amount_paid", nullable = false, precision = 15, scale = 2)
    private BigDecimal amountPaid = BigDecimal.ZERO;

    @Column(nullable = false)
    private String status = "DRAFT"; // DRAFT, APPROVED, SENT, PARTIAL, PAID, CANCELLED (OVERDUE is legacy/unused)

    @Column(name = "prepared_by_user_id")
    private UUID preparedByUserId;

    /** The retainer this invoice billed, or null for an invoice of chargeable work. */
    @Column(name = "agreement_id")
    private UUID agreementId;
    @Column(name = "approved_by_user_id")
    private UUID approvedByUserId;
    @Column(name = "approved_at")
    private Instant approvedAt;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;
    @Column(name = "cancellation_reason", length = 500)
    private String cancellationReason;

    @Column(name = "sent_at")
    private Instant sentAt;

    @Column(name = "paid_at")
    private Instant paidAt;

    @Column(name = "gl_status", nullable = false, length = 20)
    private String glStatus = "NOT_POSTED"; // NOT_POSTED, PENDING, POSTED, FAILED, LEGACY
    @Column(name = "gl_entry_id")
    private UUID glEntryId;
    @Column(name = "gl_attempts", nullable = false)
    private int glAttempts = 0;
    @Column(name = "gl_last_error", length = 500)
    private String glLastError;
    @Column(name = "gl_last_attempt_at")
    private Instant glLastAttemptAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Version
    private Long version;

    public static FmInvoice create(TenantId tenantId, UUID clientId, String invoiceNumber,
                                   LocalDate periodStart, LocalDate periodEnd, LocalDate issueDate,
                                   LocalDate dueDate, BigDecimal subtotal, BigDecimal vatAmount,
                                   BigDecimal vatRate, UUID preparedByUserId, UUID agreementId) {
        if (subtotal == null || subtotal.signum() < 0)
            throw new IllegalArgumentException("subtotal must not be negative");
        FmInvoice inv = new FmInvoice();
        inv.tenantId = tenantId;
        inv.clientId = clientId;
        inv.invoiceNumber = invoiceNumber;
        inv.periodStart = periodStart;
        inv.periodEnd = periodEnd;
        inv.issueDate = issueDate;
        inv.dueDate = dueDate;
        inv.subtotal = subtotal;
        inv.vatAmount = vatAmount != null ? vatAmount : BigDecimal.ZERO;
        inv.vatRate = vatRate != null ? vatRate : BigDecimal.ZERO;
        inv.total = inv.subtotal.add(inv.vatAmount);
        inv.preparedByUserId = preparedByUserId;
        inv.agreementId = agreementId;
        inv.status = "DRAFT";
        inv.glStatus = "NOT_POSTED";
        inv.createdAt = Instant.now();
        return inv;
    }

    /** Approval makes the invoice real: it becomes eligible to send and to post to the ledger. */
    public void approve(UUID approverId) {
        if (!"DRAFT".equals(status))
            throw new IllegalStateException("Only a DRAFT invoice can be approved — current status: " + status);
        this.status = "APPROVED";
        this.approvedByUserId = approverId;
        this.approvedAt = Instant.now();
        if ("NOT_POSTED".equals(glStatus)) this.glStatus = "PENDING"; // LEGACY drafts stay LEGACY (already posted)
    }

    /** Only an unapproved draft can be cancelled; anything approved or later needs a credit note (not yet implemented). */
    public void cancelDraft(String reason) {
        if (!"DRAFT".equals(status))
            throw new IllegalStateException("Only a DRAFT invoice can be cancelled — an approved invoice needs a credit note instead");
        if ("LEGACY".equals(glStatus))
            throw new IllegalStateException("This invoice was posted to the ledger under the previous process — issue a credit note instead of cancelling it");
        if (reason == null || reason.isBlank())
            throw new IllegalArgumentException("A reason is required to cancel an invoice");
        this.status = "CANCELLED";
        this.cancelledAt = Instant.now();
        this.cancellationReason = reason.trim();
    }

    public void markSent() {
        if (!"APPROVED".equals(status))
            throw new IllegalStateException("Only an APPROVED invoice can be sent — current status: " + status
                    + ("DRAFT".equals(status) ? " (approve it first)" : ""));
        this.status = "SENT";
        this.sentAt = Instant.now();
    }

    /** Applies a payment; the caller records the payment itself. Returns the amount applied (cents). */
    public BigDecimal recordPayment(BigDecimal amount) {
        if (amount == null || amount.signum() <= 0)
            throw new IllegalArgumentException("Payment amount must be positive");
        switch (status) {
            case "DRAFT", "APPROVED" -> throw new IllegalStateException("Send the invoice before recording a payment against it");
            case "CANCELLED" -> throw new IllegalStateException("Invoice " + invoiceNumber + " is cancelled");
            case "PAID" -> throw new IllegalStateException("Invoice " + invoiceNumber + " is already fully paid");
            default -> { /* SENT, PARTIAL (and legacy OVERDUE) accept payments */ }
        }
        BigDecimal payment = amount.setScale(2, RoundingMode.HALF_UP);
        if (payment.compareTo(balance()) > 0)
            throw new IllegalArgumentException("Payment of " + payment + " exceeds the outstanding balance of " + balance());

        this.amountPaid = this.amountPaid.add(payment);
        if (this.amountPaid.compareTo(this.total) >= 0) {
            this.status = "PAID";
            this.paidAt = Instant.now();
        } else {
            this.status = "PARTIAL";
        }
        return payment;
    }

    // ── Ledger posting status ────────────────────────────────────────────────

    public void markGlPosted(UUID journalEntryId) {
        this.glStatus = "POSTED";
        this.glEntryId = journalEntryId;
        this.glAttempts++;
        this.glLastError = null;
        this.glLastAttemptAt = Instant.now();
    }

    public void markGlFailed(String error) {
        this.glStatus = "FAILED";
        this.glAttempts++;
        this.glLastError = error != null && error.length() > 500 ? error.substring(0, 500) : error;
        this.glLastAttemptAt = Instant.now();
    }

    /** Puts a FAILED posting back in the retry queue with a fresh attempt budget. */
    public void resetGlRetry() {
        if ("FAILED".equals(glStatus)) {
            this.glStatus = "PENDING";
            this.glAttempts = 0;
        }
    }

    public BigDecimal balance() { return total.subtract(amountPaid); }
    public boolean isPaid() { return "PAID".equals(status); }
    public boolean isOverdue(LocalDate asOfDate) {
        return ("SENT".equals(status) || "PARTIAL".equals(status)) && dueDate != null && dueDate.isBefore(asOfDate);
    }
}