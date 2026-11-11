package za.co.handyflow.platform.fuel.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** A large stock adjustment waiting for a second person. Nothing moves until it is approved. */
@Entity
@Table(name = "fuel_stock_adjustment_requests")
@Getter
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class FuelStockAdjustmentRequest {

    @Id
    private UUID id = UUID.randomUUID();

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;

    @Column(name = "tank_id", nullable = false)
    private UUID tankId;

    @Column(nullable = false, precision = 14, scale = 3)
    private BigDecimal litres;

    @Column(nullable = false, length = 300)
    private String reason;

    @Column(nullable = false, length = 12)
    private String status = "PENDING";

    @Column(name = "requested_by")
    private UUID requestedBy;

    @Column(name = "requested_at", nullable = false)
    private Instant requestedAt = Instant.now();

    @Column(name = "decided_by")
    private UUID decidedBy;

    @Column(name = "decided_at")
    private Instant decidedAt;

    @Column(name = "decision_note", length = 300)
    private String decisionNote;

    @Column(name = "movement_id")
    private UUID movementId;

    @Version
    private Long version;

    public static FuelStockAdjustmentRequest create(TenantId tenantId, UUID tankId, BigDecimal litres, String reason, UUID by) {
        if (litres == null || litres.signum() == 0) throw new IllegalArgumentException("An adjustment cannot be zero litres.");
        FuelStockAdjustmentRequest r = new FuelStockAdjustmentRequest();
        r.tenantId = tenantId; r.tankId = tankId; r.litres = litres;
        r.reason = reason.trim().length() > 300 ? reason.trim().substring(0, 300) : reason.trim();
        r.requestedBy = by;
        return r;
    }

    public boolean isPending() { return "PENDING".equals(status); }

    /** A different person must decide. Throws if already decided or the approver is the requester. */
    public void approve(UUID by, UUID movementId) {
        requirePending(); requireDifferentPerson(by);
        this.status = "APPROVED"; this.decidedBy = by; this.decidedAt = Instant.now(); this.movementId = movementId;
    }

    public void reject(UUID by, String note) {
        requirePending(); requireDifferentPerson(by);
        if (note == null || note.trim().length() < 5) throw new IllegalArgumentException("Give a reason of at least 5 characters.");
        this.status = "REJECTED"; this.decidedBy = by; this.decidedAt = Instant.now(); this.decisionNote = note.trim();
    }

    /** The requester may withdraw their own request. */
    public void cancel(UUID by) {
        requirePending();
        if (by == null || !by.equals(requestedBy)) throw new IllegalStateException("Only the person who asked for this adjustment can withdraw it.");
        this.status = "CANCELLED"; this.decidedBy = by; this.decidedAt = Instant.now();
    }

    private void requirePending() { if (!isPending()) throw new IllegalStateException("This adjustment has already been " + status.toLowerCase() + "."); }

    private void requireDifferentPerson(UUID by) {
        if (by == null || by.equals(requestedBy)) throw new IllegalStateException("A second person must approve this adjustment. You cannot approve your own request.");
    }
}
