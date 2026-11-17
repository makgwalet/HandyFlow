package za.co.handyflow.platform.supplychain.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Immutable snapshot of a PO revision. Never updated or deleted after insert. */
@Entity
@Table(name = "sc_po_versions")
@Getter
@NoArgsConstructor
public class ScPoVersion {

    @Id UUID id;
    @Column(name = "tenant_id", nullable = false) UUID tenantId;
    @Column(name = "purchase_order_id", nullable = false) UUID purchaseOrderId;
    @Column(name = "revision_number", nullable = false) int revisionNumber;
    @Column(name = "amendment_id") UUID amendmentId;
    @Column(length = 500) String reason;
    @Column(nullable = false, precision = 15, scale = 2) BigDecimal subtotal;
    @Column(name = "vat_amount", nullable = false, precision = 15, scale = 2) BigDecimal vatAmount;
    @Column(name = "total_amount", nullable = false, precision = 15, scale = 2) BigDecimal totalAmount;
    @Column(name = "snapshot_json", nullable = false, columnDefinition = "TEXT") String snapshotJson;
    @Column(name = "created_by") UUID createdBy;
    @Column(name = "created_by_name", length = 255) String createdByName;
    @Column(name = "created_at", nullable = false) Instant createdAt;

    public static ScPoVersion create(UUID tenantId, UUID poId, int revision, UUID amendmentId, String reason,
                                     BigDecimal subtotal, BigDecimal vat, BigDecimal total, String snapshotJson,
                                     UUID createdBy, String createdByName) {
        ScPoVersion v = new ScPoVersion();
        v.id = UUID.randomUUID();
        v.tenantId = tenantId;
        v.purchaseOrderId = poId;
        v.revisionNumber = revision;
        v.amendmentId = amendmentId;
        v.reason = reason != null && reason.length() > 500 ? reason.substring(0, 500) : reason;
        v.subtotal = subtotal;
        v.vatAmount = vat;
        v.totalAmount = total;
        v.snapshotJson = snapshotJson;
        v.createdBy = createdBy;
        v.createdByName = createdByName;
        v.createdAt = Instant.now();
        return v;
    }
}
