package za.co.handyflow.platform.trainingprovider.domain.model;

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
 * A billing invoice for one client covering one period — same flat-
 * entity-with-computed-total shape as {@code WhseBillingInvoice} and
 * {@code CollAgencyCommissionInvoice} (no separate line-item entity;
 * see this module's status doc for that simplification, flagged the
 * same way here).
 */
@Entity
@Table(name = "trainprov_invoices")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TrainProvInvoice {

    @Id
    private UUID id = UUID.randomUUID();

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "client_id", nullable = false)
    private UUID clientId;

    @Column(name = "invoice_number", nullable = false)
    private String invoiceNumber;

    @Column(name = "period_start", nullable = false)
    private LocalDate periodStart;

    @Column(name = "period_end", nullable = false)
    private LocalDate periodEnd;

    @Column(name = "issue_date", nullable = false)
    private LocalDate issueDate;

    @Column(name = "due_date", nullable = false)
    private LocalDate dueDate;

    @Column(name = "delegate_count", nullable = false)
    private int delegateCount;

    @Column(name = "subtotal", nullable = false)
    private BigDecimal subtotal;

    @Column(name = "vat_amount", nullable = false)
    private BigDecimal vatAmount;

    @Column(name = "total", nullable = false)
    private BigDecimal total;

    @Column(name = "amount_paid", nullable = false)
    private BigDecimal amountPaid;

    /** DRAFT | SENT | PARTIAL | PAID */
    private String status;

    @Column(name = "paid_at")
    private Instant paidAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private Long version;

    public static TrainProvInvoice create(TenantId tenantId, UUID clientId, String invoiceNumber,
                                           LocalDate periodStart, LocalDate periodEnd, LocalDate issueDate,
                                           LocalDate dueDate, int delegateCount, BigDecimal subtotal,
                                           BigDecimal vatAmount) {
        TrainProvInvoice inv = new TrainProvInvoice();
        inv.tenantId = tenantId.getValue();
        inv.clientId = clientId;
        inv.invoiceNumber = invoiceNumber;
        inv.periodStart = periodStart;
        inv.periodEnd = periodEnd;
        inv.issueDate = issueDate;
        inv.dueDate = dueDate;
        inv.delegateCount = delegateCount;
        inv.subtotal = subtotal;
        inv.vatAmount = vatAmount != null ? vatAmount : BigDecimal.ZERO;
        inv.total = inv.subtotal.add(inv.vatAmount);
        inv.amountPaid = BigDecimal.ZERO;
        inv.status = "DRAFT";
        inv.createdAt = Instant.now();
        inv.updatedAt = Instant.now();
        return inv;
    }

    public void markSent() {
        if (!"DRAFT".equals(this.status)) throw new IllegalStateException("Only a DRAFT invoice can be marked sent");
        this.status = "SENT";
        this.updatedAt = Instant.now();
    }

    public void recordPayment(BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) throw new IllegalArgumentException("Payment amount must be positive");
        this.amountPaid = this.amountPaid.add(amount);
        this.status = this.amountPaid.compareTo(this.total) >= 0 ? "PAID" : "PARTIAL";
        if ("PAID".equals(this.status)) this.paidAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public BigDecimal balance() {
        return this.total.subtract(this.amountPaid);
    }
}
