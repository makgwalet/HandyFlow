package za.co.handyflow.platform.fuel.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** A refund, void or discount given on an open shift. It lowers the takings expected only once a supervisor, not the person who recorded it, approves it. */
@Entity
@Table(name = "fuel_shift_adjustments")
@Getter
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class FuelShiftAdjustment {

    @Id
    private UUID id = UUID.randomUUID();

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;

    @Column(name = "shift_id", nullable = false, updatable = false) private UUID shiftId;
    @Column(nullable = false, updatable = false, length = 10) private String kind;
    @Column(nullable = false, updatable = false, precision = 14, scale = 2) private BigDecimal amount;
    @Column(nullable = false, updatable = false, length = 300) private String reason;
    @Column(nullable = false, length = 10) private String status = "PENDING";
    @Column(name = "recorded_by") private UUID recordedBy;
    @Column(name = "recorded_at", nullable = false, updatable = false) private Instant recordedAt = Instant.now();
    @Column(name = "decided_by") private UUID decidedBy;
    @Column(name = "decided_at") private Instant decidedAt;
    @Column(name = "decision_note", length = 300) private String decisionNote;
    @Version private Long version;

    public static FuelShiftAdjustment record(TenantId tenantId, UUID shiftId, String kind, BigDecimal amount, String reason, UUID by) {
        if (!"REFUND".equals(kind) && !"VOID".equals(kind) && !"DISCOUNT".equals(kind)) throw new IllegalArgumentException("Kind must be REFUND, VOID or DISCOUNT.");
        if (amount == null || amount.signum() <= 0) throw new IllegalArgumentException("The amount must be more than R 0.");
        if (reason == null || reason.trim().length() < 5) throw new IllegalArgumentException("Give a reason of at least 5 characters.");
        FuelShiftAdjustment a = new FuelShiftAdjustment();
        a.tenantId = tenantId; a.shiftId = shiftId; a.kind = kind; a.amount = amount;
        a.reason = reason.trim().length() > 300 ? reason.trim().substring(0, 300) : reason.trim();
        a.recordedBy = by;
        return a;
    }

    public boolean isPending() { return "PENDING".equals(status); }
    public boolean isApproved() { return "APPROVED".equals(status); }

    public void approve(UUID by, String note) {
        requirePending(); requireDifferentPerson(by);
        this.status = "APPROVED"; this.decidedBy = by; this.decidedAt = Instant.now(); this.decisionNote = clean(note);
    }

    public void reject(UUID by, String note) {
        requirePending(); requireDifferentPerson(by);
        if (note == null || note.trim().length() < 5) throw new IllegalArgumentException("Give a reason of at least 5 characters.");
        this.status = "REJECTED"; this.decidedBy = by; this.decidedAt = Instant.now(); this.decisionNote = clean(note);
    }

    /** Only the person who recorded it may withdraw it, and only while it is pending. */
    public void withdraw(UUID by) {
        requirePending();
        if (by == null || !by.equals(recordedBy)) throw new IllegalStateException("Only the person who recorded this can withdraw it.");
        this.status = "WITHDRAWN"; this.decidedBy = by; this.decidedAt = Instant.now();
    }

    private void requirePending() { if (!isPending()) throw new IllegalStateException("This has already been " + status.toLowerCase() + "."); }

    private void requireDifferentPerson(UUID by) {
        if (by == null || by.equals(recordedBy)) throw new IllegalStateException("A supervisor must approve this. You cannot decide on your own entry.");
    }

    private static String clean(String n) {
        if (n == null || n.isBlank()) return null;
        return n.trim().length() > 300 ? n.trim().substring(0, 300) : n.trim();
    }
}
