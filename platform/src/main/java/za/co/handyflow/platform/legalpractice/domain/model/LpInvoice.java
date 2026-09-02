package za.co.handyflow.platform.legalpractice.domain.model;

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
 * The firm's own bill to a client. No separate line-item table — following
 * {@code RecAgencyInvoice}'s precedent (a placement invoice is
 * overwhelmingly one line, described directly on the entity rather than
 * forcing a multi-line structure the domain doesn't need) rather than
 * {@code accountant.FeeNote}'s own separate lines table. Full traceability
 * of what was actually billed still exists: every {@code LpTimeEntry}/
 * {@code LpDisbursement} rolled into this invoice is stamped with this
 * invoice's id via its own {@code markBilled(invoiceId)}, the exact
 * mechanism {@code AccTimeEntry.markBilled()} already uses. {@code matterId}
 * is nullable — a retainer-only invoice isn't tied to one matter.
 * <p>
 * VAT: flat 15%, matching {@code accountant}'s own calculation convention
 * exactly (no multi-rate/zero-rated handling — same simplification that
 * module already made).
 */
@Entity
@Table(name = "lp_invoices")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LpInvoice {

    private static final BigDecimal VAT_RATE = new BigDecimal("0.15");

    @Id
    private UUID id = UUID.randomUUID();

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;

    @Column(name = "client_id", nullable = false)
    private UUID clientId;

    @Column(name = "matter_id")
    private UUID matterId; // null for a retainer-only invoice

    @Column(name = "invoice_number", nullable = false)
    private String invoiceNumber;

    private String description;

    @Column(name = "issue_date", nullable = false)
    private LocalDate issueDate;

    @Column(name = "due_date")
    private LocalDate dueDate;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal subtotal;

    @Column(name = "vat_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal vatAmount;

    @Column(name = "total_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal totalAmount;

    @Column(name = "amount_paid", nullable = false, precision = 15, scale = 2)
    private BigDecimal amountPaid = BigDecimal.ZERO;

    @Column(nullable = false, length = 20)
    private String status; // DRAFT | SENT | PARTIALLY_PAID | PAID | WRITTEN_OFF

    private String notes;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private Long version;

    public static LpInvoice create(TenantId tenantId, UUID clientId, UUID matterId, String invoiceNumber,
                                    String description, LocalDate issueDate, LocalDate dueDate,
                                    BigDecimal subtotal, String notes) {
        LpInvoice inv = new LpInvoice();
        inv.tenantId = tenantId;
        inv.clientId = clientId;
        inv.matterId = matterId;
        inv.invoiceNumber = invoiceNumber;
        inv.description = description;
        inv.issueDate = issueDate != null ? issueDate : LocalDate.now();
        inv.dueDate = dueDate;
        inv.subtotal = subtotal;
        inv.vatAmount = subtotal.multiply(VAT_RATE).setScale(2, RoundingMode.HALF_UP);
        inv.totalAmount = inv.subtotal.add(inv.vatAmount);
        inv.amountPaid = BigDecimal.ZERO;
        inv.notes = notes;
        inv.status = "DRAFT";
        inv.createdAt = Instant.now();
        inv.updatedAt = Instant.now();
        return inv;
    }

    public void markSent() {
        if (!"DRAFT".equals(this.status)) {
            throw new IllegalStateException("Only a DRAFT invoice can be sent, current status: " + this.status);
        }
        this.status = "SENT";
        this.updatedAt = Instant.now();
    }

    public void applyPayment(BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException("Payment amount must be positive");
        }
        if ("DRAFT".equals(this.status) || "WRITTEN_OFF".equals(this.status)) {
            throw new IllegalStateException("Cannot apply a payment to an invoice in status " + this.status);
        }
        this.amountPaid = this.amountPaid.add(amount);
        this.status = this.amountPaid.compareTo(this.totalAmount) >= 0 ? "PAID" : "PARTIALLY_PAID";
        this.updatedAt = Instant.now();
    }

    public void writeOff() {
        if ("PAID".equals(this.status)) {
            throw new IllegalStateException("A fully PAID invoice cannot be written off");
        }
        this.status = "WRITTEN_OFF";
        this.updatedAt = Instant.now();
    }

    public BigDecimal getOutstandingBalance() {
        return this.totalAmount.subtract(this.amountPaid);
    }

    @PreUpdate
    void onUpdate() { this.updatedAt = Instant.now(); }
}
