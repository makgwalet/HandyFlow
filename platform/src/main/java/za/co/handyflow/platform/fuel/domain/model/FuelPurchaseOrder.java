package za.co.handyflow.platform.fuel.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** An order placed with a fuel supplier. Receipts reference it; its status follows the litres actually received. */
@Entity
@Table(name = "fuel_purchase_orders")
@Getter
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class FuelPurchaseOrder {

    /** Receiving up to this much over the ordered litres is accepted (delivery tolerance); beyond it the receipt is refused. */
    public static final BigDecimal OVER_TOLERANCE = new BigDecimal("0.02");

    @Id
    private UUID id = UUID.randomUUID();

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;

    @Column(name = "po_number", nullable = false, updatable = false, length = 20)
    private String poNumber;

    @Column(name = "supplier_id", nullable = false, updatable = false)
    private UUID supplierId;

    @Column(name = "fuel_type", nullable = false, length = 30)
    private String fuelType;

    @Column(name = "litres_ordered", nullable = false, precision = 12, scale = 2)
    private BigDecimal litresOrdered;

    @Column(name = "price_per_litre", nullable = false, precision = 10, scale = 4)
    private BigDecimal pricePerLitre;

    @Column(nullable = false, length = 16)
    private String status = "DRAFT";

    @Column(name = "expected_date")
    private LocalDate expectedDate;

    @Column(name = "cancel_reason", length = 300)
    private String cancelReason;

    @Column(name = "close_reason", length = 300)
    private String closeReason;

    @Column(name = "approval_request_id")
    private UUID approvalRequestId;

    @Column(name = "approval_note", length = 300)
    private String approvalNote;

    @Column(length = 500)
    private String notes;

    @Column(name = "created_by")
    private UUID createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Version
    private Long version;

    public static FuelPurchaseOrder create(TenantId tenantId, String poNumber, UUID supplierId, String fuelType, BigDecimal litres,
                                           BigDecimal price, LocalDate expectedDate, String notes, UUID by) {
        if (supplierId == null) throw new IllegalArgumentException("Choose a supplier.");
        FuelPurchaseOrder p = new FuelPurchaseOrder();
        p.tenantId = tenantId; p.poNumber = poNumber; p.supplierId = supplierId; p.createdBy = by; p.createdAt = Instant.now();
        p.edit(fuelType, litres, price, expectedDate, notes);
        return p;
    }

    /** Only a draft can be changed: once it has gone to the supplier the terms are what was agreed. */
    public void edit(String fuelType, BigDecimal litres, BigDecimal price, LocalDate expectedDate, String notes) {
        if (!"DRAFT".equals(status)) throw new IllegalStateException(poNumber + " has already been placed and can no longer be changed. Cancel it and raise a new one.");
        if (fuelType == null || fuelType.isBlank()) throw new IllegalArgumentException("Choose the fuel type.");
        if (litres == null || litres.signum() <= 0) throw new IllegalArgumentException("Litres must be more than zero.");
        if (price == null || price.signum() < 0) throw new IllegalArgumentException("Price cannot be negative.");
        this.fuelType = fuelType; this.litresOrdered = litres; this.pricePerLitre = price; this.expectedDate = expectedDate;
        this.notes = notes == null || notes.isBlank() ? null : notes.trim(); this.updatedAt = Instant.now();
    }

    public void place() {
        if (!"DRAFT".equals(status)) throw new IllegalStateException(poNumber + " is " + status + ", not a draft.");
        this.status = "ORDERED"; this.updatedAt = Instant.now();
    }

    /** The order needs approval before it can be placed with the supplier. */
    public void submitForApproval(UUID approvalRequestId) {
        if (!"DRAFT".equals(status)) throw new IllegalStateException(poNumber + " is " + status + ", not a draft.");
        this.status = "PENDING_APPROVAL"; this.approvalRequestId = approvalRequestId; this.approvalNote = null; this.updatedAt = Instant.now();
    }

    public boolean isPendingApproval() { return "PENDING_APPROVAL".equals(status); }

    /** Approved: the order is now placed. Does nothing unless the order is waiting for approval, so a repeated outcome is harmless. */
    public boolean approvalGranted() {
        if (!isPendingApproval()) return false;
        this.status = "ORDERED"; this.approvalNote = null; this.updatedAt = Instant.now();
        return true;
    }

    /** Rejected: back to draft so it can be corrected or cancelled. The reason is kept. */
    public boolean approvalRejected(String note) {
        if (!isPendingApproval()) return false;
        this.status = "DRAFT"; this.approvalNote = note == null ? "Approval was rejected" : note; this.updatedAt = Instant.now();
        return true;
    }

    public void cancel(String reason) {
        if (!"DRAFT".equals(status) && !"PENDING_APPROVAL".equals(status) && !"ORDERED".equals(status)) {
            throw new IllegalStateException(poNumber + " is " + status + " and cannot be cancelled" + ("RECEIVED".equals(status) || "PART_RECEIVED".equals(status) ? " because fuel has been received against it." : "."));
        }
        if (reason == null || reason.isBlank()) throw new IllegalArgumentException("Give a reason for cancelling.");
        this.status = "CANCELLED"; this.cancelReason = reason.trim(); this.updatedAt = Instant.now();
    }

    public boolean canReceive() { return "ORDERED".equals(status) || "PART_RECEIVED".equals(status); }

    /** The most that may be received in total, allowing the default delivery tolerance. */
    public BigDecimal maxReceivable() { return maxReceivable(OVER_TOLERANCE); }

    /** The most that may be received in total, allowing the given tolerance (a fraction: 0.02 is 2%). */
    public BigDecimal maxReceivable(BigDecimal tolerance) {
        return litresOrdered.multiply(BigDecimal.ONE.add(tolerance)).setScale(2, java.math.RoundingMode.DOWN);
    }

    /**
     * The supplier will not deliver the rest. Only an order that has had some fuel received can be closed short;
     * one with nothing received is cancelled instead. The litres received so far stay on the books.
     */
    public void closeShort(String reason) {
        if (!"PART_RECEIVED".equals(status)) {
            throw new IllegalStateException(poNumber + " is " + status + ". Only a part-received order can be closed short"
                    + ("ORDERED".equals(status) ? "; nothing has been received on it, so cancel it instead." : "."));
        }
        if (reason == null || reason.isBlank() || reason.trim().length() < 5) throw new IllegalArgumentException("Say why the rest will not be delivered (at least 5 characters).");
        this.status = "CLOSED_SHORT"; this.closeReason = reason.trim(); this.updatedAt = Instant.now();
    }

    /** Sets the status from the litres received so far (already net of reversed receipts). Does nothing to draft or cancelled orders. */
    public void applyReceived(BigDecimal received) { applyReceived(received, OVER_TOLERANCE); }

    /** As above, treating an order as fully received once it is within the given tolerance (a fraction) of the litres ordered. */
    public void applyReceived(BigDecimal received, BigDecimal tolerance) {
        if (!canReceive() && !"RECEIVED".equals(status)) return;
        BigDecimal full = litresOrdered.multiply(BigDecimal.ONE.subtract(tolerance));
        String next = received.signum() <= 0 ? "ORDERED" : received.compareTo(full) >= 0 ? "RECEIVED" : "PART_RECEIVED";
        if (!next.equals(status)) { this.status = next; this.updatedAt = Instant.now(); }
    }

    public void softDelete() {
        if (!"DRAFT".equals(status) && !"CANCELLED".equals(status)) throw new IllegalStateException("Only a draft or cancelled order can be removed.");
        this.deletedAt = Instant.now(); this.updatedAt = Instant.now();
    }

    @PreUpdate
    void onUpdate() { this.updatedAt = Instant.now(); }
}
