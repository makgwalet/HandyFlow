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
 *   APPROVED/SENT/PARTIAL --credit note--> reduces the balance; a fully credited, unpaid invoice becomes CREDITED
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
    private String status = "DRAFT"; // DRAFT, APPROVED, SENT, PARTIAL, PAID, CREDITED, CANCELLED (OVERDUE is legacy/unused)

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

    /** Total of APPROVED credit notes against this invoice (VAT inclusive). */
    @Column(name = "credited_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal creditedAmount = BigDecimal.ZERO;

    // Snapshot of both parties taken when the invoice is drafted/approved, so later edits to a client's address or
    // VAT number never rewrite an invoice that has been issued.
    @Column(name = "supplier_name") private String supplierName;
    @Column(name = "supplier_vat_number", length = 30) private String supplierVatNumber;
    @Column(name = "supplier_address", length = 500) private String supplierAddress;
    @Column(name = "client_name") private String clientName;
    @Column(name = "client_vat_number", length = 10) private String clientVatNumber;
    @Column(name = "client_address", length = 500) private String clientAddress;

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

    /** Above this total (VAT inclusive) a tax invoice must also carry the recipient's name and address (VAT Act s20(4)). */
    public static final BigDecimal RECIPIENT_DETAILS_THRESHOLD = new BigDecimal("5000.00");

    /** Records who is billing whom, as at now. Only a DRAFT can change its snapshot. */
    public void snapshotParties(String supplierName, String supplierVatNumber, String supplierAddress,
                                String clientName, String clientVatNumber, String clientAddress) {
        if (!"DRAFT".equals(status))
            throw new IllegalStateException("The parties on an issued invoice cannot change — current status: " + status);
        this.supplierName = supplierName;
        this.supplierVatNumber = supplierVatNumber;
        this.supplierAddress = supplierAddress;
        this.clientName = clientName;
        this.clientVatNumber = clientVatNumber;
        this.clientAddress = clientAddress;
    }

    /** A tax invoice is one issued by a VAT vendor. Anything else is just an invoice. */
    public boolean isTaxInvoice() {
        return supplierVatNumber != null && !supplierVatNumber.isBlank();
    }

    /**
     * South African tax-invoice content rules. A VAT vendor's invoice needs its own name, VAT number and address;
     * above R5,000 it also needs the recipient's name and address. (The recipient's VAT number is printed when
     * known — the law only requires it where the recipient is a registered vendor claiming input tax.)
     */
    public void requireIssuable() {
        if (!isTaxInvoice()) return;
        if (isBlank(supplierName) || isBlank(supplierAddress))
            throw new IllegalStateException("A tax invoice needs the company name and address — complete the company profile (address) first");
        if (total.compareTo(RECIPIENT_DETAILS_THRESHOLD) > 0 && (isBlank(clientName) || isBlank(clientAddress)))
            throw new IllegalStateException("A tax invoice above R5,000 must show the client's name and address — add the address to the client first");
    }

    private static boolean isBlank(String s) { return s == null || s.isBlank(); }

    /** Approval makes the invoice real: it becomes eligible to send and to post to the ledger. */
    public void approve(UUID approverId) {
        if (!"DRAFT".equals(status))
            throw new IllegalStateException("Only a DRAFT invoice can be approved — current status: " + status);
        requireIssuable();
        this.status = "APPROVED";
        this.approvedByUserId = approverId;
        this.approvedAt = Instant.now();
        if ("NOT_POSTED".equals(glStatus)) this.glStatus = "PENDING"; // LEGACY drafts stay LEGACY (already posted)
    }

    /** Only an unapproved draft can be cancelled; anything approved or later needs a credit note. */
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
            case "CREDITED" -> throw new IllegalStateException("Invoice " + invoiceNumber + " has been fully credited — there is nothing to pay");
            default -> { /* SENT, PARTIAL (and legacy OVERDUE) accept payments */ }
        }
        BigDecimal payment = amount.setScale(2, RoundingMode.HALF_UP);
        if (payment.compareTo(balance()) > 0)
            throw new IllegalArgumentException("Payment of " + payment + " exceeds the outstanding balance of " + balance());

        this.amountPaid = this.amountPaid.add(payment);
        if (balance().signum() <= 0) {
            this.status = "PAID";
            this.paidAt = Instant.now();
        } else {
            this.status = "PARTIAL";
        }
        return payment;
    }

    /**
     * Applies an APPROVED credit note (VAT inclusive amount). Only an issued, still-open invoice can be credited, and
     * never for more than is outstanding — refunding money already received is a separate, deliberate step.
     */
    public void applyCredit(BigDecimal creditTotal) {
        if (creditTotal == null || creditTotal.signum() <= 0)
            throw new IllegalArgumentException("Credit amount must be positive");
        switch (status) {
            case "DRAFT" -> throw new IllegalStateException("A draft invoice is cancelled, not credited");
            case "CANCELLED" -> throw new IllegalStateException("Invoice " + invoiceNumber + " is cancelled");
            case "PAID" -> throw new IllegalStateException("Invoice " + invoiceNumber + " is already fully paid — there is no outstanding balance to credit");
            case "CREDITED" -> throw new IllegalStateException("Invoice " + invoiceNumber + " has already been fully credited");
            default -> { /* APPROVED, SENT, PARTIAL (and legacy OVERDUE) */ }
        }
        if (creditTotal.compareTo(balance()) > 0)
            throw new IllegalArgumentException("Credit of " + creditTotal + " exceeds the outstanding balance of " + balance());
        this.creditedAmount = this.creditedAmount.add(creditTotal);
        if (balance().signum() == 0) {
            if (amountPaid.signum() > 0) {
                this.status = "PAID";
                this.paidAt = Instant.now();
            } else {
                this.status = "CREDITED";
            }
        }
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

    public BigDecimal balance() { return total.subtract(amountPaid).subtract(creditedAmount); }
    public boolean isPaid() { return "PAID".equals(status); }

    /** Whether the invoice is in the ledger, so a credit note against it can be posted too. */
    public boolean isInLedger() { return "POSTED".equals(glStatus) || "LEGACY".equals(glStatus); }
    public boolean isOverdue(LocalDate asOfDate) {
        return ("SENT".equals(status) || "PARTIAL".equals(status)) && dueDate != null && dueDate.isBefore(asOfDate);
    }
}