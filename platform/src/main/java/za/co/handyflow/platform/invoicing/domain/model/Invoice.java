package za.co.handyflow.platform.invoicing.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "invoices")
@Getter
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class Invoice {

    @Id
    private UUID id = UUID.randomUUID();

    @Embedded
    @AttributeOverride(name = "value",
            column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;

    @Column(name = "customer_id", nullable = true)
    private UUID customerId;

    @Column(name = "quote_id")
    private UUID quoteId;

    @Column(name = "invoice_number", nullable = false)
    private String invoiceNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private InvoiceStatus status;

    private String title;
    private String notes;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal subtotal;

    @Column(name = "vat_total", nullable = false, precision = 15, scale = 2)
    private BigDecimal vatTotal;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal total;

    @Column(name = "amount_paid", nullable = false, precision = 15, scale = 2)
    private BigDecimal amountPaid = BigDecimal.ZERO;

    @Column(nullable = false)
    private String currency = "ZAR";

    @Column(name = "due_date")
    private LocalDate dueDate;

    @Column(name = "issued_at")
    private Instant issuedAt;

    @Column(name = "paid_at")
    private Instant paidAt;

    @OneToMany(mappedBy = "invoice", cascade = CascadeType.ALL,
            orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("sortOrder ASC")
    private List<InvoiceLineItem> lineItems = new ArrayList<>();

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Column(name = "deleted_by")
    private UUID deletedBy;

    @Column(name = "walkin_client_name")
    private String walkinClientName;

    @Column(name = "walkin_client_email")
    private String walkinClientEmail;

    @Column(name = "walkin_client_phone")
    private String walkinClientPhone;

    @Enumerated(EnumType.STRING)
    @Column(name = "invoice_type", nullable = false)
    private InvoiceType invoiceType = InvoiceType.STANDARD;

    /**
     * For RECURRING_INSTANCE invoices — links back to the schedule that created
     * this invoice.  Null for STANDARD and RETAINER invoices.
     */
    @Column(name = "recurring_schedule_id")
    private UUID recurringScheduleId;

    /**
     * The minimum hours the client must pay for upfront before machine release.
     * Only populated for RETAINER invoices.
     */
    @Column(name = "committed_hours", precision = 10, scale = 2)
    private BigDecimal committedHours;

    /** Rate charged per hour.  committedHours × ratePerHour = initial retainer amount. */
    @Column(name = "rate_per_hour", precision = 12, scale = 2)
    private BigDecimal ratePerHour;

    /**
     * Actual hours consumed so far (logged via POST /invoices/{id}/hours).
     * Starts at 0; when hoursConsumed > committedHours the invoice becomes
     * RETAINER_OVERAGE and a reconciliation invoice should be raised.
     */
    @Column(name = "hours_consumed", precision = 10, scale = 2)
    private BigDecimal hoursConsumed = BigDecimal.ZERO;

    @Column(name = "credit_amount", nullable = false)
    private BigDecimal creditAmount = BigDecimal.ZERO;

    @Version
    private Long version;

    public static Invoice createFromQuote(
            TenantId tenantId,
            UUID customerId,
            UUID quoteId,
            String invoiceNumber,
            java.math.BigDecimal subtotal,
            java.math.BigDecimal vatTotal,
            java.math.BigDecimal total,
            String walkinClientName,
            String walkinClientEmail,
            String walkinClientPhone
    ) {
        var inv = new Invoice();
        inv.id               = java.util.UUID.randomUUID();
        inv.tenantId         = tenantId;
        inv.customerId       = customerId;
        inv.quoteId          = quoteId;
        inv.invoiceNumber    = invoiceNumber;
        inv.invoiceType      = InvoiceType.STANDARD;
        inv.subtotal         = subtotal;
        inv.vatTotal         = vatTotal;
        inv.total            = total;
        inv.amountPaid       = java.math.BigDecimal.ZERO;
        inv.creditAmount     = java.math.BigDecimal.ZERO;
        inv.hoursConsumed    = java.math.BigDecimal.ZERO;
        inv.status           = InvoiceStatus.DRAFT;
        inv.currency         = "ZAR";
        inv.walkinClientName  = walkinClientName;
        inv.walkinClientEmail = walkinClientEmail;
        inv.walkinClientPhone = walkinClientPhone;
        inv.createdAt        = java.time.Instant.now();
        inv.updatedAt        = java.time.Instant.now();
        return inv;
    }

    public void addLineItem(InvoiceLineItem item) {
        this.lineItems.add(item);
    }

    public void issue(LocalDate dueDate) {
        this.status = InvoiceStatus.ISSUED;
        this.issuedAt = Instant.now();
        this.dueDate = dueDate;
        this.updatedAt = Instant.now();
    }

    public void recordPayment(BigDecimal amount, Instant paidAt) {
        if (status == InvoiceStatus.CANCELLED) {
            throw new IllegalStateException("Cannot record payment on a cancelled invoice");
        }
        // Always accumulate — supports multiple partial payments
        this.amountPaid = (this.amountPaid == null ? BigDecimal.ZERO : this.amountPaid).add(amount);
        this.paidAt     = paidAt;

        int cmp = this.amountPaid.compareTo(this.total);

        if (cmp < 0) {
            this.status      = InvoiceStatus.PARTIALLY_PAID;
            this.creditAmount = BigDecimal.ZERO;
        } else if (cmp == 0) {
            this.status      = InvoiceStatus.PAID;
            this.creditAmount = BigDecimal.ZERO;
        } else {
            // amountPaid > total
            this.status       = InvoiceStatus.OVERPAID;
            this.creditAmount = this.amountPaid.subtract(this.total);
        }
    }

    public void markIssued() {
        if (this.status == InvoiceStatus.DRAFT) {
            this.status = InvoiceStatus.ISSUED;
            this.issuedAt = Instant.now();
            this.updatedAt = Instant.now();
        }
    }

    /**
     * Creates an upfront / retainer invoice.
     *
     * The initial total is committedHours × ratePerHour (+ VAT).
     * Line items are added separately (one line for the committed hours block
     * is conventional, but callers can add more).
     */
    public static Invoice createRetainer(
            TenantId tenantId,
            UUID customerId,
            String invoiceNumber,
            String title,
            java.math.BigDecimal committedHours,
            java.math.BigDecimal ratePerHour,
            String walkinClientName,
            String walkinClientEmail,
            String walkinClientPhone
    ) {
        var inv = new Invoice();
        inv.id               = java.util.UUID.randomUUID();
        inv.tenantId         = tenantId;
        inv.customerId       = customerId;
        inv.invoiceNumber    = invoiceNumber;
        inv.title            = title;
        inv.invoiceType      = InvoiceType.RETAINER;
        inv.committedHours   = committedHours;
        inv.ratePerHour      = ratePerHour;
        inv.hoursConsumed    = java.math.BigDecimal.ZERO;
        inv.creditAmount     = java.math.BigDecimal.ZERO;
        inv.amountPaid       = java.math.BigDecimal.ZERO;
        inv.subtotal         = java.math.BigDecimal.ZERO;
        inv.vatTotal         = java.math.BigDecimal.ZERO;
        inv.total            = java.math.BigDecimal.ZERO;
        inv.status           = InvoiceStatus.DRAFT;
        inv.currency         = "ZAR";
        inv.walkinClientName  = walkinClientName;
        inv.walkinClientEmail = walkinClientEmail;
        inv.walkinClientPhone = walkinClientPhone;
        inv.createdAt        = java.time.Instant.now();
        inv.updatedAt        = java.time.Instant.now();
        return inv;
    }

    /** Factory for invoices spawned by a recurring schedule. */
    public static Invoice createFromSchedule(
            TenantId tenantId,
            UUID customerId,
            UUID scheduleId,
            String invoiceNumber,
            String title,
            java.math.BigDecimal subtotal,
            java.math.BigDecimal vatTotal,
            java.math.BigDecimal total,
            String walkinClientName,
            String walkinClientEmail,
            String walkinClientPhone
    ) {
        var inv = new Invoice();
        inv.id                  = java.util.UUID.randomUUID();
        inv.tenantId            = tenantId;
        inv.customerId          = customerId;
        inv.recurringScheduleId = scheduleId;
        inv.invoiceNumber       = invoiceNumber;
        inv.title               = title;
        inv.invoiceType         = InvoiceType.RECURRING_INSTANCE;
        inv.subtotal            = subtotal;
        inv.vatTotal            = vatTotal;
        inv.total               = total;
        inv.amountPaid          = java.math.BigDecimal.ZERO;
        inv.creditAmount        = java.math.BigDecimal.ZERO;
        inv.hoursConsumed       = java.math.BigDecimal.ZERO;
        inv.status              = InvoiceStatus.DRAFT;
        inv.currency            = "ZAR";
        inv.walkinClientName    = walkinClientName;
        inv.walkinClientEmail   = walkinClientEmail;
        inv.walkinClientPhone   = walkinClientPhone;
        inv.createdAt           = java.time.Instant.now();
        inv.updatedAt           = java.time.Instant.now();
        return inv;
    }

// ── Hours logging behaviour ───────────────────────────────────────────────────

    /**
     * Log consumed hours against a retainer invoice.
     * Returns true if this tips the invoice into overage (consumed > committed).
     */
    public boolean logHours(BigDecimal hours) {
        if (invoiceType != InvoiceType.RETAINER) {
            throw new IllegalStateException("Cannot log hours on a non-retainer invoice");
        }
        this.hoursConsumed = this.hoursConsumed.add(hours);
        return committedHours != null && hoursConsumed.compareTo(committedHours) > 0;
    }

    public BigDecimal getRemainingHours() {
        if (committedHours == null) return BigDecimal.ZERO;
        return committedHours.subtract(hoursConsumed).max(BigDecimal.ZERO);
    }

    public boolean isOverage() {
        return committedHours != null && hoursConsumed.compareTo(committedHours) > 0;
    }

    // ── Totals recalculation ──────────────────────────────────────────────────

    /**
     * Recomputes subtotal, vatTotal, and total from the current line items.
     *
     * WHY a manual recalculate rather than @Formula or @Transient computed fields?
     * Line items are added one at a time and the invoice is saved once after all
     * items are attached.  Computed DB formulas would require a flush/reload cycle
     * after every addLineItem() call.  Explicit recalculation keeps the entity
     * self-contained and testable without a DB round-trip.
     */
    public void recalculateTotals() {
        this.subtotal = lineItems.stream()
                .map(InvoiceLineItem::getLineTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        this.vatTotal = lineItems.stream()
                .map(InvoiceLineItem::getVatAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        this.total = this.subtotal.add(this.vatTotal);
    }

    public BigDecimal getCreditAmount() {
        return creditAmount != null ? creditAmount : BigDecimal.ZERO;
    }

    @PreUpdate
    void onUpdate() { this.updatedAt = Instant.now(); }
}
