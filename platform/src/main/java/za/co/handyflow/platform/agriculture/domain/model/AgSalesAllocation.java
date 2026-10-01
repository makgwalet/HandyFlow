package za.co.handyflow.platform.agriculture.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.agriculture.domain.rules.AgCostAllocation;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Links part of an invoice line (owned by Invoicing) to a production target (ADR-001, W2). It stores NO money: revenue is computed live from
 * the invoice, so it follows credit notes and cancellations. For livestock it is also the only record that a sale happened (when and how many);
 * it does not change the herd.
 */
@Entity
@Table(name = "ag_sales_allocations")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AgSalesAllocation {

    public static final String ACTIVE = "ACTIVE";
    public static final String REMOVED = "REMOVED";

    @Id
    private UUID id = UUID.randomUUID();

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;

    @Column(name = "farm_id", nullable = false)
    private UUID farmId;

    @Column(name = "invoice_id", nullable = false)
    private UUID invoiceId;

    @Column(name = "invoice_line_id", nullable = false)
    private UUID invoiceLineId;

    @Column(name = "invoice_number")
    private String invoiceNumber;           // display copy

    private String description;             // display copy

    @Column(name = "target_type", nullable = false)
    private String targetType;              // CROP_CYCLE | GROUP | ANIMAL | ENTERPRISE

    @Column(name = "target_id", nullable = false)
    private UUID targetId;

    @Column(nullable = false, precision = 14, scale = 3)
    private BigDecimal quantity;            // in the invoice line's own unit

    private String unit;

    @Column(name = "head_count")
    private Integer headCount;              // livestock sold, when it applies

    @Column(name = "sold_on", nullable = false)
    private LocalDate soldOn;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Column(nullable = false)
    private String status = ACTIVE;         // ACTIVE | REMOVED

    @Column(name = "removed_at")
    private Instant removedAt;

    @Column(name = "removed_by")
    private UUID removedBy;

    @Column(name = "created_by")
    private UUID createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private Long version;

    public static AgSalesAllocation create(TenantId tenantId, UUID farmId, UUID invoiceId, UUID invoiceLineId, String invoiceNumber,
                                           String description, String targetType, UUID targetId, BigDecimal quantity, String unit,
                                           Integer headCount, LocalDate soldOn, String notes, UUID createdBy) {
        if (tenantId == null) throw new IllegalArgumentException("tenantId is required");
        if (farmId == null) throw new IllegalArgumentException("farmId is required");
        if (invoiceId == null) throw new IllegalArgumentException("invoiceId is required");
        if (invoiceLineId == null) throw new IllegalArgumentException("invoiceLineId is required");
        if (targetType == null || !AgCostAllocation.TARGET_TYPES.contains(targetType)) throw new IllegalArgumentException("targetType must be one of " + AgCostAllocation.TARGET_TYPES);
        if (targetId == null) throw new IllegalArgumentException("targetId is required");
        if (quantity == null || quantity.signum() <= 0) throw new IllegalArgumentException("quantity must be positive");
        if (headCount != null && headCount <= 0) throw new IllegalArgumentException("headCount must be positive");
        if (headCount != null && AgCostAllocation.ANIMAL.equals(targetType) && headCount != 1) {
            throw new IllegalArgumentException("a single animal is one head");
        }
        if (soldOn == null) throw new IllegalArgumentException("soldOn is required");

        AgSalesAllocation a = new AgSalesAllocation();
        a.tenantId = tenantId;
        a.farmId = farmId;
        a.invoiceId = invoiceId;
        a.invoiceLineId = invoiceLineId;
        a.invoiceNumber = invoiceNumber;
        a.description = description != null && description.length() > 255 ? description.substring(0, 255) : description;
        a.targetType = targetType;
        a.targetId = targetId;
        a.quantity = quantity;
        a.unit = unit == null || unit.isBlank() ? null : unit.trim();
        a.headCount = headCount;
        a.soldOn = soldOn;
        a.notes = notes;
        a.createdBy = createdBy;
        a.createdAt = Instant.now();
        a.updatedAt = a.createdAt;
        return a;
    }

    /** Takes this allocation out of revenue. It stays on record as REMOVED, with who and when. */
    public void remove(UUID by) {
        if (!ACTIVE.equals(status)) throw new IllegalStateException("sales allocation is already " + status);
        this.status = REMOVED;
        this.removedAt = Instant.now();
        this.removedBy = by;
        this.updatedAt = this.removedAt;
    }

    @PreUpdate
    void onUpdate() { this.updatedAt = Instant.now(); }
}
