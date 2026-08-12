package za.co.handyflow.platform.payrollbureau.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * An invoice the bureau sends to one of its clients for payroll services
 * rendered. Same status-lifecycle shape as accountant.FeeNote
 * (DRAFT -> SENT -> PARTIAL/PAID) — that pattern is proven and worth
 * reusing — but line generation is per-employee/per-pay-run, not
 * time-entry-based, since payroll bureau billing and professional-
 * services billing are genuinely different business models. Forcing
 * this onto FeeNote's time-entry generation would fit the shape at the
 * cost of the substance.
 */
@Entity
@Table(name = "pay_fee_notes")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PayFeeNote {

    @Id
    private UUID id = UUID.randomUUID();

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "pay_client_id", nullable = false)
    private UUID payClientId;

    @Column(name = "invoice_number", nullable = false)
    private String invoiceNumber;

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

    public static PayFeeNote create(UUID tenantId, UUID payClientId, String invoiceNumber,
                                    LocalDate invoiceDate, LocalDate dueDate,
                                    BigDecimal subtotal, BigDecimal vatAmount) {
        PayFeeNote f = new PayFeeNote();
        f.tenantId = tenantId;
        f.payClientId = payClientId;
        f.invoiceNumber = invoiceNumber;
        f.invoiceDate = invoiceDate;
        f.dueDate = dueDate;
        f.subtotal = subtotal;
        f.vatAmount = vatAmount;
        f.total = subtotal.add(vatAmount);
        f.status = "DRAFT";
        f.createdAt = Instant.now();
        return f;
    }

    public void markSent() {
        if (!"DRAFT".equals(status)) throw new IllegalStateException("Only DRAFT fee notes can be sent");
        this.status = "SENT";
        this.sentAt = Instant.now();
    }

    /** Same balance-tracking shape as accountant.FeeNote.recordPayment(). */
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