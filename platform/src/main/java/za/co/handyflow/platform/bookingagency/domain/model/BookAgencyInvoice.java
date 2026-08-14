package za.co.handyflow.platform.bookingagency.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * A flat monthly retainer invoice — genuinely different shape from
 * RecAgencyInvoice (tied to one specific placement) since a retainer
 * charge isn't tied to any single booking. Tracks a BILLING PERIOD
 * instead, with a uniqueness constraint (see migration) preventing the
 * same client being billed twice for the same month — the structural
 * equivalent of "one invoice per placement" for a periodic charge
 * rather than a per-event one.
 */
@Entity
@Table(name = "booka_invoices")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BookAgencyInvoice {

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

    @Column(name = "period_start", nullable = false)
    private LocalDate periodStart;

    @Column(name = "period_end", nullable = false)
    private LocalDate periodEnd;

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

    public static BookAgencyInvoice create(UUID tenantId, UUID clientId, String invoiceNumber,
                                           String description, LocalDate periodStart, LocalDate periodEnd,
                                           LocalDate invoiceDate, LocalDate dueDate, BigDecimal subtotal, BigDecimal vatAmount) {
        BookAgencyInvoice inv = new BookAgencyInvoice();
        inv.tenantId = tenantId;
        inv.clientId = clientId;
        inv.invoiceNumber = invoiceNumber;
        inv.description = description;
        inv.periodStart = periodStart;
        inv.periodEnd = periodEnd;
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