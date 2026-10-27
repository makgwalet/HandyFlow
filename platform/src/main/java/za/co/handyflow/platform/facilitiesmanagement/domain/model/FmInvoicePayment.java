package za.co.handyflow.platform.facilitiesmanagement.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;

/**
 * One payment received against an invoice, with its own ledger-posting status (DR bank / CR accounts
 * receivable). Payment history was previously just a running total on the invoice. LEGACY rows are
 * back-filled from invoices paid before this existed and are never posted.
 */
@Entity
@Table(name = "fm_invoice_payments")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FmInvoicePayment {

    public static final Set<String> METHODS = Set.of("EFT", "CASH", "CARD", "CHEQUE", "OTHER");

    @Id
    private UUID id = UUID.randomUUID();

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;

    @Column(name = "invoice_id", nullable = false)
    private UUID invoiceId;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal amount;

    @Column(name = "payment_date", nullable = false)
    private LocalDate paymentDate;

    @Column(length = 30)
    private String method;

    @Column(length = 100)
    private String reference;

    @Column(name = "recorded_by_user_id")
    private UUID recordedByUserId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "gl_status", nullable = false, length = 20)
    private String glStatus = "PENDING"; // PENDING, POSTED, FAILED, LEGACY
    @Column(name = "gl_entry_id")
    private UUID glEntryId;
    @Column(name = "gl_attempts", nullable = false)
    private int glAttempts = 0;
    @Column(name = "gl_last_error", length = 500)
    private String glLastError;
    @Column(name = "gl_last_attempt_at")
    private Instant glLastAttemptAt;

    public static FmInvoicePayment create(TenantId tenantId, UUID invoiceId, BigDecimal amount, LocalDate paymentDate,
                                          String method, String reference, UUID recordedByUserId) {
        if (amount == null || amount.signum() <= 0)
            throw new IllegalArgumentException("Payment amount must be positive");
        LocalDate date = paymentDate != null ? paymentDate : LocalDate.now();
        if (date.isAfter(LocalDate.now()))
            throw new IllegalArgumentException("paymentDate cannot be in the future");
        String normalizedMethod = null;
        if (method != null && !method.isBlank()) {
            normalizedMethod = method.trim().toUpperCase();
            if (!METHODS.contains(normalizedMethod))
                throw new IllegalArgumentException("method must be one of " + METHODS);
        }
        if (reference != null && reference.length() > 100)
            throw new IllegalArgumentException("reference must be at most 100 characters");

        FmInvoicePayment p = new FmInvoicePayment();
        p.tenantId = tenantId;
        p.invoiceId = invoiceId;
        p.amount = amount;
        p.paymentDate = date;
        p.method = normalizedMethod;
        p.reference = reference != null && !reference.isBlank() ? reference.trim() : null;
        p.recordedByUserId = recordedByUserId;
        p.createdAt = Instant.now();
        return p;
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