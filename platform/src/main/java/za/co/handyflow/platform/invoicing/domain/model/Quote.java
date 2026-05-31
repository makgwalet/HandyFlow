package za.co.handyflow.platform.invoicing.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "quotes")
@Getter
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class Quote {

    @Id
    private UUID id = UUID.randomUUID();

    @Embedded
    @AttributeOverride(name = "value",
            column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;

    @Column(name = "customer_id", nullable = true)
    private UUID customerId;

    @Column(name = "quote_number", nullable = false)
    private String quoteNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private QuoteStatus status;

    private String title;
    private String notes;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal subtotal = BigDecimal.ZERO;

    @Column(name = "vat_total", nullable = false, precision = 15, scale = 2)
    private BigDecimal vatTotal = BigDecimal.ZERO;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal total = BigDecimal.ZERO;

    @Column(nullable = false)
    private String currency = "ZAR";

    @Column(name = "sent_at")
    private Instant sentAt;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "accepted_at")
    private Instant acceptedAt;

    @Column(name = "rejected_at")
    private Instant rejectedAt;

    @OneToMany(mappedBy = "quote", cascade = CascadeType.ALL,
            orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("sortOrder ASC")
    private List<QuoteLineItem> lineItems = new ArrayList<>();

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

    @Version
    private Long version;

    public static Quote create(TenantId tenantId, UUID customerId,
                               String quoteNumber, String title,
                               String walkinClientName, String walkinClientEmail,
                               String walkinClientPhone) {
        Quote q = new Quote();
        q.tenantId = tenantId;
        q.customerId = customerId;
        q.quoteNumber = quoteNumber;
        q.title = title;
        q.walkinClientName = walkinClientName;
        q.walkinClientEmail = walkinClientEmail;
        q.walkinClientPhone = walkinClientPhone;
        q.status = QuoteStatus.DRAFT;
        q.createdAt = Instant.now();
        q.updatedAt = Instant.now();
        return q;
    }

    public void send() {
        if (lineItems.isEmpty()) {
            throw new IllegalStateException(
                    "Cannot send a quote with no line items"
            );
        }
        validateStatus(QuoteStatus.SENT);
        this.status = QuoteStatus.SENT;
        this.sentAt = Instant.now();
        // WHY 30 days? Business rule from product spec.
        this.expiresAt = Instant.now().plus(30, ChronoUnit.DAYS);
        this.updatedAt = Instant.now();
    }

    public void accept() {
        validateStatus(QuoteStatus.ACCEPTED);
        this.status = QuoteStatus.ACCEPTED;
        this.acceptedAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public void reject() {
        validateStatus(QuoteStatus.REJECTED);
        this.status = QuoteStatus.REJECTED;
        this.rejectedAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public void expire() {
        if (this.status != QuoteStatus.SENT) return;
        this.status = QuoteStatus.EXPIRED;
        this.updatedAt = Instant.now();
    }

    public void markInvoiced() {
        this.status = QuoteStatus.INVOICED;
        this.updatedAt = Instant.now();
    }

    public void softDelete(UUID deletedByUserId) {
        if (this.status == QuoteStatus.INVOICED) {
            throw new IllegalStateException(
                    "Cannot delete an invoiced quote"
            );
        }
        this.deletedAt = Instant.now();
        this.deletedBy = deletedByUserId;
        this.updatedAt = Instant.now();
    }

    public void addLineItem(QuoteLineItem item) {
        if (status != QuoteStatus.DRAFT) {
            throw new IllegalStateException(
                    "Cannot modify a quote that is not in DRAFT status"
            );
        }
        this.lineItems.add(item);
        recalculateTotals();
    }

    public void recalculateTotals() {
        this.subtotal = lineItems.stream()
                .map(QuoteLineItem::getLineTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        this.vatTotal = lineItems.stream()
                .map(QuoteLineItem::getVatAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        this.total = subtotal.add(vatTotal);
        this.updatedAt = Instant.now();
    }

    public boolean isExpired() {
        return status == QuoteStatus.SENT
                && expiresAt != null
                && Instant.now().isAfter(expiresAt);
    }

    public boolean isEditable() {
        return status == QuoteStatus.DRAFT;
    }

    private void validateStatus(QuoteStatus target) {
        if (!status.canTransitionTo(target)) {
            throw new IllegalStateException(
                    "Cannot transition quote from %s to %s"
                            .formatted(status, target)
            );
        }
    }

    public boolean isDeleted() { return deletedAt != null; }

    @PreUpdate
    void onUpdate() { this.updatedAt = Instant.now(); }
}