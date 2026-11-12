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
 * A request to buy fuel (B05). Draft, then submitted to the Approvals engine (no rule means approved straight away), then
 * converted into a draft purchase order. A rejected requisition goes back to draft with the reason so it can be corrected.
 */
@Entity
@Table(name = "fuel_purchase_requisitions")
@Getter
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class FuelPurchaseRequisition {

    @Id
    private UUID id = UUID.randomUUID();

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;

    @Column(name = "req_number", nullable = false, updatable = false, length = 20)
    private String reqNumber;

    @Column(nullable = false, length = 20)
    private String status = "DRAFT";

    @Column(name = "fuel_type", nullable = false, length = 30)
    private String fuelType;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal litres;

    @Column(name = "tank_id")
    private UUID tankId;

    @Column(name = "supplier_id")
    private UUID supplierId;

    @Column(name = "estimated_price", precision = 12, scale = 4)
    private BigDecimal estimatedPrice;

    @Column(name = "needed_by")
    private LocalDate neededBy;

    @Column(nullable = false, length = 300)
    private String reason;

    @Column(name = "requested_by", updatable = false)
    private UUID requestedBy;

    @Column(name = "approval_request_id")
    private UUID approvalRequestId;

    @Column(name = "approval_note", length = 300)
    private String approvalNote;

    @Column(name = "purchase_order_id")
    private UUID purchaseOrderId;

    @Column(name = "cancel_reason", length = 300)
    private String cancelReason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    @Version
    private Long version;

    public static FuelPurchaseRequisition create(TenantId tenantId, String number, String fuelType, BigDecimal litres, UUID tankId, UUID supplierId,
                                                 BigDecimal estimatedPrice, LocalDate neededBy, String reason, UUID by) {
        FuelPurchaseRequisition r = new FuelPurchaseRequisition();
        r.tenantId = tenantId; r.reqNumber = number; r.requestedBy = by;
        r.fill(fuelType, litres, tankId, supplierId, estimatedPrice, neededBy, reason);
        return r;
    }

    public void edit(String fuelType, BigDecimal litres, UUID tankId, UUID supplierId, BigDecimal estimatedPrice, LocalDate neededBy, String reason) {
        if (!"DRAFT".equals(status)) throw new IllegalStateException(reqNumber + " is " + label() + " and can no longer be changed.");
        fill(fuelType, litres, tankId, supplierId, estimatedPrice, neededBy, reason);
    }

    private void fill(String fuelType, BigDecimal litres, UUID tankId, UUID supplierId, BigDecimal estimatedPrice, LocalDate neededBy, String reason) {
        if (fuelType == null || fuelType.isBlank()) throw new IllegalArgumentException("Choose the fuel type.");
        if (litres == null || litres.signum() <= 0) throw new IllegalArgumentException("Litres must be more than zero.");
        if (estimatedPrice != null && estimatedPrice.signum() < 0) throw new IllegalArgumentException("The estimated price cannot be negative.");
        if (reason == null || reason.trim().length() < 5) throw new IllegalArgumentException("Say why this fuel is needed (at least 5 characters).");
        this.fuelType = fuelType.trim(); this.litres = litres; this.tankId = tankId; this.supplierId = supplierId; this.estimatedPrice = estimatedPrice;
        this.neededBy = neededBy; this.reason = reason.trim().length() > 300 ? reason.trim().substring(0, 300) : reason.trim(); this.updatedAt = Instant.now();
    }

    /** What the approver is asked to judge. Approval cannot assess value without a price. */
    public BigDecimal estimatedValue() {
        if (estimatedPrice == null) throw new IllegalStateException("Add an estimated price per litre before submitting, so approval can judge the value.");
        return litres.multiply(estimatedPrice).setScale(2, java.math.RoundingMode.HALF_UP);
    }

    public void submitForApproval(UUID approvalRequestId) {
        requireDraft("submitted");
        this.status = "PENDING_APPROVAL"; this.approvalRequestId = approvalRequestId; this.approvalNote = null; this.updatedAt = Instant.now();
    }

    /** No approval was needed. */
    public void approveDirectly() {
        requireDraft("approved");
        this.status = "APPROVED"; this.approvalNote = null; this.updatedAt = Instant.now();
    }

    public boolean isPendingApproval() { return "PENDING_APPROVAL".equals(status); }

    /** Does nothing unless waiting, so a repeated outcome is harmless. Returns whether anything changed. */
    public boolean approvalGranted() {
        if (!isPendingApproval()) return false;
        this.status = "APPROVED"; this.approvalNote = null; this.updatedAt = Instant.now();
        return true;
    }

    public boolean approvalRejected(String note) {
        if (!isPendingApproval()) return false;
        this.status = "DRAFT"; this.approvalNote = note == null ? "Approval was rejected" : note; this.updatedAt = Instant.now();
        return true;
    }

    public void convertedTo(UUID purchaseOrderId) {
        if (!"APPROVED".equals(status)) throw new IllegalStateException(reqNumber + " is " + label() + ". Only an approved requisition can become a purchase order.");
        this.status = "CONVERTED"; this.purchaseOrderId = purchaseOrderId; this.updatedAt = Instant.now();
    }

    public void cancel(String reason) {
        if (!"DRAFT".equals(status) && !"PENDING_APPROVAL".equals(status) && !"APPROVED".equals(status)) {
            throw new IllegalStateException(reqNumber + " is " + label() + " and cannot be cancelled.");
        }
        if (reason == null || reason.trim().length() < 5) throw new IllegalArgumentException("Give a reason of at least 5 characters.");
        this.status = "CANCELLED"; this.cancelReason = reason.trim().length() > 300 ? reason.trim().substring(0, 300) : reason.trim(); this.updatedAt = Instant.now();
    }

    private void requireDraft(String what) {
        if (!"DRAFT".equals(status)) throw new IllegalStateException(reqNumber + " is " + label() + ", so it cannot be " + what + ".");
    }

    private String label() { return status.toLowerCase().replace('_', ' '); }
}
