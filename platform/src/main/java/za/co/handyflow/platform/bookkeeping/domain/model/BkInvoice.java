package za.co.handyflow.platform.bookkeeping.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * A flat-entity invoice the practice raises against a client — mirrors
 * {@code FmInvoice}/{@code TrainProvInvoice}'s own shape (no line
 * items; a RETAINER invoice is a single flat fee, a TIME_AND_MATERIALS
 * invoice is the summed total of the period's billed {@link
 * BkTimeEntry} records, not itemised per entry on the invoice record
 * itself — the underlying time entries remain individually queryable
 * via their own {@code invoiceId}).
 */
@Entity
@Table(name = "bk_invoices")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BkInvoice {

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

    @Column(name = "vat_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal vatAmount;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal total;

    @Column(name = "amount_paid", nullable = false, precision = 15, scale = 2)
    private BigDecimal amountPaid = BigDecimal.ZERO;

    @Column(nullable = false)
    private String status = "DRAFT"; // DRAFT, SENT, PARTIAL, PAID, OVERDUE

    @Column(name = "sent_at")
    private Instant sentAt;

    @Column(name = "paid_at")
    private Instant paidAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Version
    private Long version;

    public static BkInvoice create(TenantId tenantId, UUID clientId, String invoiceNumber,
                                    LocalDate periodStart, LocalDate periodEnd, LocalDate issueDate,
                                    LocalDate dueDate, BigDecimal subtotal, BigDecimal vatAmount) {
        if (subtotal == null || subtotal.signum() < 0)
            throw new IllegalArgumentException("subtotal must not be negative");
        BkInvoice inv = new BkInvoice();
        inv.tenantId = tenantId;
        inv.clientId = clientId;
        inv.invoiceNumber = invoiceNumber;
        inv.periodStart = periodStart;
        inv.periodEnd = periodEnd;
        inv.issueDate = issueDate;
        inv.dueDate = dueDate;
        inv.subtotal = subtotal;
        inv.vatAmount = vatAmount != null ? vatAmount : BigDecimal.ZERO;
        inv.total = inv.subtotal.add(inv.vatAmount);
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
        if (amount == null || amount.signum() <= 0)
            throw new IllegalArgumentException("Payment amount must be positive");
        this.amountPaid = this.amountPaid.add(amount);
        if (this.amountPaid.compareTo(this.total) >= 0) {
            this.status = "PAID";
            this.paidAt = Instant.now();
        } else if (this.amountPaid.compareTo(BigDecimal.ZERO) > 0) {
            this.status = "PARTIAL";
        }
    }

    public BigDecimal balance() { return total.subtract(amountPaid); }
    public boolean isPaid() { return "PAID".equals(status); }
    public boolean isOverdue(LocalDate asOfDate) {
        return !isPaid() && dueDate != null && dueDate.isBefore(asOfDate);
    }
}
