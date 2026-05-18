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

    @Column(name = "customer_id", nullable = false)
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

    @Version
    private Long version;

    public static Invoice createFromQuote(TenantId tenantId, UUID customerId,
                                          UUID quoteId, String invoiceNumber,
                                          BigDecimal subtotal, BigDecimal vatTotal,
                                          BigDecimal total) {
        Invoice inv = new Invoice();
        inv.tenantId = tenantId;
        inv.customerId = customerId;
        inv.quoteId = quoteId;
        inv.invoiceNumber = invoiceNumber;
        inv.status = InvoiceStatus.DRAFT;
        inv.subtotal = subtotal;
        inv.vatTotal = vatTotal;
        inv.total = total;
        inv.amountPaid = BigDecimal.ZERO;
        inv.createdAt = Instant.now();
        inv.updatedAt = Instant.now();
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

    @PreUpdate
    void onUpdate() { this.updatedAt = Instant.now(); }
}
