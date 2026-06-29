// security/domain/model/Shift.java

package za.co.handyflow.platform.security.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "security_shifts")
@Getter
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class Shift {

    @Id
    private UUID id = UUID.randomUUID();

    @Embedded
    @AttributeOverride(name = "value",
            column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;

    @Column(name = "site_id", nullable = false)
    private UUID siteId;

    @Column(name = "guard_id", nullable = false)
    private UUID guardId;

    @Column(name = "start_at", nullable = false)
    private Instant startAt;

    @Column(name = "end_at", nullable = false)
    private Instant endAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ShiftStatus status;

    private String notes;

    /**
     * Minimum checkpoint scans required before this shift can be marked COMPLETED.
     * 0 = no enforcement (default, backward-compatible).
     * Set > 0 at shift creation to enforce proof-of-patrol (fixes bug #17).
     * Phase 1: derive from site.patrol_required_scans automatically.
     */
    @Column(name = "min_scan_count", nullable = false)
    private int minScanCount = 0;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Column(name = "deleted_by")
    private UUID deletedBy;

    @Version
    private Long version;

    public static Shift create(TenantId tenantId, UUID siteId, UUID guardId,
                               Instant startAt, Instant endAt, String notes) {
        if (!endAt.isAfter(startAt)) {
            throw new IllegalArgumentException("Shift end must be after start");
        }
        Shift s = new Shift();
        s.tenantId  = tenantId;
        s.siteId    = siteId;
        s.guardId   = guardId;
        s.startAt   = startAt;
        s.endAt     = endAt;
        s.status    = ShiftStatus.SCHEDULED;
        s.notes     = notes;
        s.createdAt = Instant.now();
        s.updatedAt = Instant.now();
        return s;
    }

    public void start() {
        if (status != ShiftStatus.SCHEDULED) {
            throw new IllegalStateException("Only SCHEDULED shifts can be started");
        }
        this.status    = ShiftStatus.ACTIVE;
        this.updatedAt = Instant.now();
    }

    public void complete() {
        if (status != ShiftStatus.ACTIVE) {
            throw new IllegalStateException("Only ACTIVE shifts can be completed");
        }
        this.status    = ShiftStatus.COMPLETED;
        this.updatedAt = Instant.now();
    }

    public void miss() {
        if (status != ShiftStatus.SCHEDULED) return;
        this.status    = ShiftStatus.MISSED;
        this.updatedAt = Instant.now();
    }

    public void cancel(UUID cancelledBy) {
        this.status    = ShiftStatus.CANCELLED;
        this.deletedAt = Instant.now();
        this.deletedBy = cancelledBy;
        this.updatedAt = Instant.now();
    }

    public boolean isDeleted() { return deletedAt != null; }

    /**
     * Re-assigns this shift to a different guard after a swap is approved.
     *
     * WHY a dedicated domain method rather than a setter?
     * The reassignment must be traceable — it happens only through an approved
     * ShiftSwapRequest, and the shift's updatedAt reflects the change.
     * A generic setGuardId() setter would allow silent reassignment anywhere,
     * making the audit trail impossible to reconstruct.
     * This method is only called from ShiftSwapService.approveSwap().
     */
    public void reassignGuard(UUID newGuardId) {
        if (this.status != ShiftStatus.SCHEDULED) {
            throw new IllegalStateException(
                    "Only SCHEDULED shifts can be reassigned via swap (status: " + this.status + ")");
        }
        this.guardId   = newGuardId;
        this.updatedAt = java.time.Instant.now();
    }

    @PreUpdate
    void onUpdate() { this.updatedAt = Instant.now(); }
}