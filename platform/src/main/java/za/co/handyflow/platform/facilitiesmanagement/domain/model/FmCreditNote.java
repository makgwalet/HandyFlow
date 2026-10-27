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
 * A credit note reduces what a client owes on an issued invoice (a billing error, a goodwill reduction, a disputed
 * charge). It mirrors the invoice workflow so the same controls apply:
 * <pre>
 *   DRAFT --approve--> APPROVED   (reduces the invoice balance, posts to the ledger)
 *   DRAFT --cancel---> CANCELLED
 * </pre>
 * The person who prepares a credit note cannot approve it. An approved credit note is permanent — to undo one, issue
 * a new invoice. VAT is credited at the rate of the invoice it relates to, so a credit never reclaims more VAT than
 * was charged. Under the VAT Act s21 a credit note must identify the original tax invoice, state the reason and show
 * the amounts credited and the VAT; the parties are snapshotted from that invoice.
 */
@Entity
@Table(name = "fm_credit_notes")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FmCreditNote {

    @Id
    private UUID id = UUID.randomUUID();

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;

    @Column(name = "credit_note_number", nullable = false, unique = true)
    private String creditNoteNumber;

    @Column(name = "invoice_id", nullable = false)
    private UUID invoiceId;

    @Column(name = "client_id", nullable = false)
    private UUID clientId;

    @Column(name = "issue_date", nullable = false)
    private LocalDate issueDate;

    @Column(nullable = false, length = 500)
    private String reason;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal subtotal;

    @Column(name = "vat_rate", nullable = false, precision = 5, scale = 2)
    private BigDecimal vatRate = BigDecimal.ZERO;

    @Column(name = "vat_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal vatAmount = BigDecimal.ZERO;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal total;

    @Column(nullable = false, length = 20)
    private String status = "DRAFT"; // DRAFT, APPROVED, CANCELLED

    @Column(name = "prepared_by_user_id")
    private UUID preparedByUserId;
    @Column(name = "approved_by_user_id")
    private UUID approvedByUserId;
    @Column(name = "approved_at")
    private Instant approvedAt;
    @Column(name = "cancelled_at")
    private Instant cancelledAt;
    @Column(name = "cancellation_reason", length = 500)
    private String cancellationReason;

    @Column(name = "supplier_name") private String supplierName;
    @Column(name = "supplier_vat_number", length = 30) private String supplierVatNumber;
    @Column(name = "supplier_address", length = 500) private String supplierAddress;
    @Column(name = "client_name") private String clientName;
    @Column(name = "client_vat_number", length = 10) private String clientVatNumber;
    @Column(name = "client_address", length = 500) private String clientAddress;

    @Column(name = "gl_status", nullable = false, length = 20)
    private String glStatus = "NOT_POSTED"; // NOT_POSTED, PENDING, POSTED, FAILED
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

    /** Credit a VAT-exclusive amount; VAT is added at the invoice's rate. */
    public static FmCreditNote forSubtotal(FmInvoice invoice, java.util.function.Supplier<String> creditNoteNumber, LocalDate issueDate,
                                           BigDecimal subtotal, String reason, UUID preparedBy) {
        if (subtotal == null || subtotal.signum() <= 0)
            throw new IllegalArgumentException("The credit amount must be positive");
        BigDecimal net = subtotal.setScale(2, RoundingMode.HALF_UP);
        BigDecimal vat = net.multiply(invoice.getVatRate()).divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
        return build(invoice, creditNoteNumber, issueDate, net, vat, reason, preparedBy);
    }

    /**
     * Credit exactly what is outstanding. The total is the balance, and the VAT is backed out of it
     * (total x rate / (100 + rate)), so crediting the remainder always settles the invoice to the cent.
     */
    public static FmCreditNote forFullBalance(FmInvoice invoice, java.util.function.Supplier<String> creditNoteNumber, LocalDate issueDate,
                                              String reason, UUID preparedBy) {
        BigDecimal total = invoice.balance();
        if (total.signum() <= 0)
            throw new IllegalStateException("Invoice " + invoice.getInvoiceNumber() + " has no outstanding balance to credit");
        BigDecimal vat = total.multiply(invoice.getVatRate())
                .divide(new BigDecimal("100").add(invoice.getVatRate()), 2, RoundingMode.HALF_UP);
        return build(invoice, creditNoteNumber, issueDate, total.subtract(vat), vat, reason, preparedBy);
    }

    private static FmCreditNote build(FmInvoice invoice, java.util.function.Supplier<String> number, LocalDate issueDate, BigDecimal net,
                                      BigDecimal vat, String reason, UUID preparedBy) {
        if (reason == null || reason.isBlank())
            throw new IllegalArgumentException("A reason is required for a credit note");
        if (reason.trim().length() > 500)
            throw new IllegalArgumentException("reason must be at most 500 characters");
        if (!isCreditable(invoice.getStatus()))
            throw new IllegalStateException("Invoice " + invoice.getInvoiceNumber() + " is " + invoice.getStatus()
                    + " — only an approved, sent or part-paid invoice can be credited");
        FmCreditNote n = new FmCreditNote();
        n.tenantId = invoice.getTenantId();
        n.invoiceId = invoice.getId();
        n.clientId = invoice.getClientId();
        n.issueDate = issueDate;
        n.reason = reason.trim();
        n.subtotal = net;
        n.vatRate = invoice.getVatRate();
        n.vatAmount = vat;
        n.total = net.add(vat);
        if (n.total.compareTo(invoice.balance()) > 0)
            throw new IllegalArgumentException("A credit of " + n.total + " exceeds the outstanding balance of " + invoice.balance()
                    + " on " + invoice.getInvoiceNumber());
        n.preparedByUserId = preparedBy;
        n.supplierName = invoice.getSupplierName();
        n.supplierVatNumber = invoice.getSupplierVatNumber();
        n.supplierAddress = invoice.getSupplierAddress();
        n.clientName = invoice.getClientName();
        n.clientVatNumber = invoice.getClientVatNumber();
        n.clientAddress = invoice.getClientAddress();
        n.creditNoteNumber = number.get(); // numbered last, so a rejected credit note never burns a number from the series
        n.createdAt = Instant.now();
        return n;
    }

    private static boolean isCreditable(String invoiceStatus) {
        return "APPROVED".equals(invoiceStatus) || "SENT".equals(invoiceStatus) || "PARTIAL".equals(invoiceStatus)
                || "OVERDUE".equals(invoiceStatus);
    }

    /** Approving applies the credit to the invoice (the caller does that) and queues the ledger posting. */
    public void approve(UUID approverId) {
        if (!"DRAFT".equals(status))
            throw new IllegalStateException("Only a DRAFT credit note can be approved — current status: " + status);
        this.status = "APPROVED";
        this.approvedByUserId = approverId;
        this.approvedAt = Instant.now();
        this.glStatus = "PENDING";
    }

    public void cancelDraft(String reason) {
        if (!"DRAFT".equals(status))
            throw new IllegalStateException("Only a DRAFT credit note can be cancelled — current status: " + status);
        if (reason == null || reason.isBlank())
            throw new IllegalArgumentException("A reason is required to cancel a credit note");
        this.status = "CANCELLED";
        this.cancelledAt = Instant.now();
        this.cancellationReason = reason.trim();
    }

    public boolean isTaxCreditNote() {
        return supplierVatNumber != null && !supplierVatNumber.isBlank();
    }

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

    public void resetGlRetry() {
        if ("FAILED".equals(glStatus)) {
            this.glStatus = "PENDING";
            this.glAttempts = 0;
        }
    }
}
