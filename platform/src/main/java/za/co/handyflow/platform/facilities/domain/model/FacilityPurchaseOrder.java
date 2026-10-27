package za.co.handyflow.platform.facilities.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * A purchase order for stock items from one vendor. DRAFT (editable) -> APPROVED (by someone other than its
 * creator) -> PARTLY_RECEIVED -> RECEIVED; or CANCELLED while nothing has been received. Receiving more than was
 * ordered is refused. A part-delivered order the vendor will not complete can be closed short.
 */
@Entity
@Table(name = "facility_purchase_orders")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FacilityPurchaseOrder {

    public static final int MAX_LINES = 50;

    @Id
    private UUID id = UUID.randomUUID();

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;

    @Column(name = "po_number", nullable = false, length = 40)
    private String poNumber;

    @Column(name = "vendor_id", nullable = false)
    private UUID vendorId;

    @Column(nullable = false, length = 20)
    private String status = "DRAFT";

    @Column(name = "expected_date")
    private LocalDate expectedDate;

    @Column(length = 1000)
    private String notes;

    @Column(name = "created_by_user_id")
    private UUID createdByUserId;

    @Column(name = "approved_by_user_id")
    private UUID approvedByUserId;

    @Column(name = "approved_at")
    private Instant approvedAt;

    @Column(name = "closed_reason", length = 500)
    private String closedReason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @JoinColumn(name = "purchase_order_id", nullable = false)
    @OrderBy("lineNo ASC")
    private List<FacilityPurchaseOrderLine> lines = new ArrayList<>();

    public record LineSpec(UUID itemId, String description, BigDecimal quantity, BigDecimal unitCost) {}

    public static FacilityPurchaseOrder create(TenantId tenantId, String poNumber, UUID vendorId, LocalDate expectedDate,
                                               String notes, UUID createdBy, List<LineSpec> specs) {
        FacilityPurchaseOrder po = new FacilityPurchaseOrder();
        po.tenantId = tenantId;
        po.poNumber = poNumber;
        po.vendorId = vendorId;
        po.expectedDate = expectedDate;
        po.notes = blankToNull(notes);
        po.createdByUserId = createdBy;
        po.createdAt = Instant.now();
        po.updatedAt = po.createdAt;
        po.setLines(specs);
        return po;
    }

    /** Replace vendor, dates and lines of a draft. */
    public void revise(UUID vendorId, LocalDate expectedDate, String notes, List<LineSpec> specs) {
        requireStatus("DRAFT", "Only a draft order can be edited");
        this.vendorId = vendorId;
        this.expectedDate = expectedDate;
        this.notes = blankToNull(notes);
        setLines(specs);
        this.updatedAt = Instant.now();
    }

    private void setLines(List<LineSpec> specs) {
        if (specs == null || specs.isEmpty()) throw new IllegalArgumentException("A purchase order needs at least one line");
        if (specs.size() > MAX_LINES) throw new IllegalArgumentException("A purchase order can have at most " + MAX_LINES + " lines");
        java.util.Set<UUID> seen = new java.util.HashSet<>();
        for (LineSpec s : specs)
            if (!seen.add(s.itemId())) throw new IllegalArgumentException("An item can appear on an order only once");
        lines.clear();
        int n = 1;
        for (LineSpec s : specs) lines.add(FacilityPurchaseOrderLine.of(tenantId, n++, s));
    }

    public void approve(UUID approver) {
        requireStatus("DRAFT", "Only a draft order can be approved");
        this.status = "APPROVED";
        this.approvedByUserId = approver;
        this.approvedAt = Instant.now();
        this.updatedAt = this.approvedAt;
    }

    /** Book a delivery of {@code quantity} against a line and move the order's status on. */
    public void receiveLine(UUID lineId, BigDecimal quantity) {
        if (!"APPROVED".equals(status) && !"PARTLY_RECEIVED".equals(status))
            throw new IllegalStateException("Goods can only be received against an approved order — status is " + status);
        FacilityPurchaseOrderLine line = lines.stream().filter(l -> l.getId().equals(lineId)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Line " + lineId + " is not on order " + poNumber));
        line.receive(quantity);
        this.status = lines.stream().allMatch(FacilityPurchaseOrderLine::isComplete) ? "RECEIVED" : "PARTLY_RECEIVED";
        this.updatedAt = Instant.now();
    }

    public void cancel(String reason) {
        if ("RECEIVED".equals(status) || "CANCELLED".equals(status))
            throw new IllegalStateException("A " + status + " order cannot be cancelled");
        if (lines.stream().anyMatch(l -> l.getReceivedQty().signum() > 0))
            throw new IllegalStateException("Goods have already been received; close the order short instead");
        String why = requireReason(reason);
        this.status = "CANCELLED";
        this.closedReason = why;
        this.updatedAt = Instant.now();
    }

    /** Accept what has arrived and stop expecting the rest. */
    public void closeShort(String reason) {
        requireStatus("PARTLY_RECEIVED", "Only a part-received order can be closed short");
        String why = requireReason(reason);
        this.status = "RECEIVED";
        this.closedReason = why;
        this.updatedAt = Instant.now();
    }

    public BigDecimal totalValue() {
        return lines.stream().map(FacilityPurchaseOrderLine::lineTotal).reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
    }

    public boolean isOpen() { return "APPROVED".equals(status) || "PARTLY_RECEIVED".equals(status); }

    private void requireStatus(String expected, String message) {
        if (!expected.equals(status)) throw new IllegalStateException(message + " — status is " + status);
    }

    private static String requireReason(String reason) {
        if (reason == null || reason.isBlank()) throw new IllegalArgumentException("a reason is required");
        return reason.trim();
    }

    private static String blankToNull(String s) { return s == null || s.isBlank() ? null : s.trim(); }
}
