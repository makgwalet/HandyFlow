package za.co.handyflow.platform.fuel.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Stage 2: a fuel sales order for one product line. Starts as a quotation (QUOTE), becomes CONFIRMED
 * once the credit check passes and fuel is allocated from a tank, and follows its deliveries
 * (PART_DELIVERED, DELIVERED). Delivered litres always come from completed deliveries, never typed in.
 */
@Entity
@Table(name = "fuel_sales_orders")
@Getter
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class FuelSalesOrder {

    public static final String QUOTE = "QUOTE", CONFIRMED = "CONFIRMED", PART_DELIVERED = "PART_DELIVERED",
            DELIVERED = "DELIVERED", CANCELLED = "CANCELLED";

    @Id
    private UUID id = UUID.randomUUID();

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;

    @Column(name = "order_number", nullable = false, updatable = false, length = 20)
    private String orderNumber;

    @Column(name = "account_id", nullable = false, updatable = false)
    private UUID accountId;

    @Column(name = "crm_customer_id", nullable = false, updatable = false)
    private UUID crmCustomerId;

    @Column(name = "site_id")
    private UUID siteId;

    @Column(name = "fuel_type", nullable = false, length = 30)
    private String fuelType;

    @Column(name = "litres_ordered", nullable = false, precision = 12, scale = 2)
    private BigDecimal litresOrdered;

    @Column(name = "price_per_litre", nullable = false, precision = 10, scale = 4)
    private BigDecimal pricePerLitre;

    @Column(nullable = false, length = 20)
    private String status = QUOTE;

    @Column(name = "requested_date")
    private LocalDate requestedDate;

    @Column(name = "quote_valid_until")
    private LocalDate quoteValidUntil;

    @Column(name = "tank_id")
    private UUID tankId;

    @Column(name = "credit_override_reason", length = 300)
    private String creditOverrideReason;

    @Column(name = "cancel_reason", length = 300)
    private String cancelReason;

    @Column(name = "customer_reference", length = 60)
    private String customerReference;

    @Column(length = 500)
    private String notes;

    @Column(name = "created_by", updatable = false)
    private UUID createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Version
    private Long version;

    public static FuelSalesOrder create(TenantId tenantId, String orderNumber, UUID accountId, UUID crmCustomerId, UUID siteId,
                                        String fuelType, BigDecimal litres, BigDecimal price, LocalDate requestedDate,
                                        LocalDate validUntil, String customerReference, String notes, UUID by) {
        FuelSalesOrder o = new FuelSalesOrder();
        o.tenantId = tenantId; o.orderNumber = orderNumber; o.accountId = accountId; o.crmCustomerId = crmCustomerId;
        o.createdBy = by; o.createdAt = Instant.now();
        o.revise(siteId, fuelType, litres, price, requestedDate, validUntil, customerReference, notes);
        return o;
    }

    /** Quotations can be edited; once confirmed the commercial terms are fixed (cancel and re-quote instead). */
    public void revise(UUID siteId, String fuelType, BigDecimal litres, BigDecimal price, LocalDate requestedDate,
                       LocalDate validUntil, String customerReference, String notes) {
        if (!QUOTE.equals(status)) throw new IllegalStateException("Only a quotation can be edited. Cancel this order and raise a new one.");
        if (fuelType == null || fuelType.isBlank()) throw new IllegalArgumentException("Choose the fuel type.");
        if (litres == null || litres.signum() <= 0) throw new IllegalArgumentException("Litres must be greater than zero.");
        if (price == null || price.signum() < 0) throw new IllegalArgumentException("Price per litre cannot be negative.");
        this.siteId = siteId; this.fuelType = fuelType.trim(); this.litresOrdered = litres; this.pricePerLitre = price;
        this.requestedDate = requestedDate; this.quoteValidUntil = validUntil;
        this.customerReference = blankToNull(customerReference); this.notes = blankToNull(notes);
        this.updatedAt = Instant.now();
    }

    public BigDecimal value() { return litresOrdered.multiply(pricePerLitre); }

    public boolean isOpen() { return CONFIRMED.equals(status) || PART_DELIVERED.equals(status); }

    public void confirm(UUID tankId, String overrideReason, LocalDate today) {
        if (!QUOTE.equals(status)) throw new IllegalStateException("Only a quotation can be confirmed (this order is " + status.toLowerCase().replace('_', ' ') + ").");
        if (quoteValidUntil != null && today.isAfter(quoteValidUntil)) throw new IllegalStateException("This quotation expired on " + quoteValidUntil + ". Edit the validity date to confirm it.");
        if (tankId == null) throw new IllegalArgumentException("Choose the tank to supply this order from.");
        this.tankId = tankId; this.creditOverrideReason = blankToNull(overrideReason);
        this.status = CONFIRMED; this.updatedAt = Instant.now();
    }

    /** Follows the delivered litres. Only open orders move; a cancelled or quoted one never changes. */
    public void followDelivered(BigDecimal delivered) {
        if (!isOpen() && !DELIVERED.equals(status)) return;
        if (delivered.compareTo(litresOrdered) >= 0) status = DELIVERED;
        else if (delivered.signum() > 0) status = PART_DELIVERED;
        else status = CONFIRMED;
        this.updatedAt = Instant.now();
    }

    public void cancel(String reason) {
        if (CANCELLED.equals(status) || DELIVERED.equals(status)) throw new IllegalStateException("A " + status.toLowerCase() + " order cannot be cancelled.");
        if (reason == null || reason.trim().length() < 5) throw new IllegalArgumentException("Give a reason of at least 5 characters.");
        this.status = CANCELLED; this.cancelReason = reason.trim(); this.updatedAt = Instant.now();
    }

    private static String blankToNull(String s) { return s == null || s.isBlank() ? null : s.trim(); }

    @PreUpdate
    void onUpdate() { this.updatedAt = Instant.now(); }
}
