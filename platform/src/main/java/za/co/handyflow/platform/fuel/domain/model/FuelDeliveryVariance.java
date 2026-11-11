package za.co.handyflow.platform.fuel.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.UUID;

/**
 * Litres loaded on the tanker versus litres delivered, outside the company's tolerance. Never written off automatically:
 * a person reviews it and records a decision. Recording a decision does not move stock; it is the audit record.
 */
@Entity
@Table(name = "fuel_delivery_variances")
@Getter
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class FuelDeliveryVariance {

    @Id
    private UUID id = UUID.randomUUID();

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;

    @Column(name = "delivery_id", nullable = false)
    private UUID deliveryId;

    @Column(name = "loaded_litres", nullable = false, precision = 12, scale = 2)
    private BigDecimal loadedLitres;

    @Column(name = "delivered_litres", nullable = false, precision = 12, scale = 2)
    private BigDecimal deliveredLitres;

    /** Delivered minus loaded: negative means fuel is unaccounted for. */
    @Column(name = "variance_litres", nullable = false, precision = 12, scale = 2)
    private BigDecimal varianceLitres;

    @Column(name = "variance_pct", nullable = false, precision = 8, scale = 3)
    private BigDecimal variancePct;

    @Column(name = "tolerance_pct", nullable = false, precision = 5, scale = 2)
    private BigDecimal tolerancePct;

    @Column(nullable = false, length = 14)
    private String status = "OPEN";

    @Column(length = 12)
    private String decision;

    @Column(length = 300)
    private String note;

    @Column(name = "reviewed_by")
    private UUID reviewedBy;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    @Column(name = "detected_at", nullable = false)
    private Instant detectedAt = Instant.now();

    @Version
    private Long version;

    /** The variance as a percentage of litres loaded, always positive. Zero when nothing was loaded. */
    public static BigDecimal pctOf(BigDecimal loaded, BigDecimal delivered) {
        if (loaded == null || loaded.signum() <= 0 || delivered == null) return BigDecimal.ZERO;
        return delivered.subtract(loaded).abs().multiply(new BigDecimal("100")).divide(loaded, 3, RoundingMode.HALF_UP);
    }

    /** Returns a new variance if the difference is outside the tolerance, otherwise null. A null tolerance means the check is off. */
    public static FuelDeliveryVariance ifOutside(TenantId tenantId, UUID deliveryId, BigDecimal loaded, BigDecimal delivered, BigDecimal tolerancePct) {
        if (tolerancePct == null || loaded == null || delivered == null || loaded.signum() <= 0) return null;
        BigDecimal pct = pctOf(loaded, delivered);
        if (pct.compareTo(tolerancePct) <= 0) return null;
        FuelDeliveryVariance v = new FuelDeliveryVariance();
        v.tenantId = tenantId; v.deliveryId = deliveryId; v.loadedLitres = loaded; v.deliveredLitres = delivered;
        v.varianceLitres = delivered.subtract(loaded); v.variancePct = pct; v.tolerancePct = tolerancePct;
        return v;
    }

    public void investigate(UUID by, String note) {
        if (!"OPEN".equals(status)) throw new IllegalStateException("Only an open variance can be moved to investigating. This one is " + status.toLowerCase() + ".");
        this.status = "INVESTIGATING"; this.note = requireNote(note); this.reviewedBy = by; this.reviewedAt = Instant.now();
    }

    public void close(UUID by, String decision, String note) {
        if ("CLOSED".equals(status)) throw new IllegalStateException("This variance is already closed.");
        if (!"EXPLAINED".equals(decision) && !"WRITTEN_OFF".equals(decision)) throw new IllegalArgumentException("The decision must be EXPLAINED or WRITTEN_OFF.");
        if (by == null) throw new IllegalStateException("A reviewer is required.");
        this.decision = decision; this.note = requireNote(note); this.status = "CLOSED"; this.reviewedBy = by; this.reviewedAt = Instant.now();
    }

    private static String requireNote(String note) {
        if (note == null || note.trim().length() < 5) throw new IllegalArgumentException("Give a note of at least 5 characters.");
        String n = note.trim();
        return n.length() > 300 ? n.substring(0, 300) : n;
    }
}
