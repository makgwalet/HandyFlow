// security/domain/model/ShiftSwapRequest.java

package za.co.handyflow.platform.security.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.util.UUID;

/**
 * ShiftSwapRequest — a guard's request to swap their shift with another guard.
 *
 * Two-stage approval:
 *   PENDING              → proposed guard accepts  → PROPOSED_ACCEPTED
 *   PROPOSED_ACCEPTED    → supervisor approves     → APPROVED  (shift re-assigned)
 *                        → supervisor rejects      → REJECTED
 *   PENDING / PROPOSED_ACCEPTED → requesting guard cancels → CANCELLED
 *
 * WHY require the proposed guard to accept before a supervisor sees it?
 * Without this step a supervisor would approve a swap only to find the
 * replacement guard is unavailable, on leave, or unaware of the assignment.
 * The two-stage model mirrors how physical shift swaps actually work:
 * the guard asking first confirms with the colleague, then both go to the
 * supervisor for sign-off.
 *
 * WHY store validationNotes on the request?
 * The validation (overlap check, PSiRA status, grade requirements) runs
 * at approval time and its result is frozen on the row.  If a guard's
 * PSiRA expires between request and approval, the supervisor sees the
 * validation failure even if the guard's record was updated in between.
 */
@Entity
@Table(name = "security_shift_swap_requests")
@Getter
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class ShiftSwapRequest {

    @Id
    private UUID id = UUID.randomUUID();

    @Embedded
    @AttributeOverride(name = "value",
            column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;

    @Column(name = "original_shift_id", nullable = false)
    private UUID originalShiftId;

    @Column(name = "requesting_guard_id", nullable = false)
    private UUID requestingGuardId;

    @Column(name = "proposed_guard_id")
    private UUID proposedGuardId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SwapStatus status = SwapStatus.PENDING;

    @Column(name = "proposed_accepted_at")
    private Instant proposedAcceptedAt;

    @Column(name = "requested_at", nullable = false, updatable = false)
    private Instant requestedAt;

    @Column(name = "decided_by")
    private UUID decidedBy;

    @Column(name = "decided_at")
    private Instant decidedAt;

    @Column
    private String reason;

    @Column(name = "rejection_reason")
    private String rejectionReason;

    @Column(name = "validation_passed")
    private Boolean validationPassed;

    @Column(name = "validation_notes")
    private String validationNotes;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    // ── Factory ────────────────────────────────────────────────────────────────

    public static ShiftSwapRequest create(
            TenantId tenantId, UUID originalShiftId,
            UUID requestingGuardId, UUID proposedGuardId, String reason) {
        ShiftSwapRequest r = new ShiftSwapRequest();
        r.tenantId          = tenantId;
        r.originalShiftId   = originalShiftId;
        r.requestingGuardId = requestingGuardId;
        r.proposedGuardId   = proposedGuardId;
        r.reason            = reason;
        r.status            = SwapStatus.PENDING;
        r.requestedAt       = Instant.now();
        r.createdAt         = Instant.now();
        r.updatedAt         = Instant.now();
        return r;
    }

    // ── State transitions ──────────────────────────────────────────────────────

    /**
     * The proposed guard accepts the swap.
     * Moves: PENDING → PROPOSED_ACCEPTED
     */
    public void proposedGuardAccepts() {
        assertStatus(SwapStatus.PENDING, "accept");
        this.status             = SwapStatus.PROPOSED_ACCEPTED;
        this.proposedAcceptedAt = Instant.now();
        this.updatedAt          = Instant.now();
    }

    /**
     * Supervisor approves — runs all validation checks before calling.
     * Moves: PROPOSED_ACCEPTED → APPROVED
     */
    public void approve(UUID decidedBy, boolean validationPassed, String validationNotes) {
        assertStatus(SwapStatus.PROPOSED_ACCEPTED, "approve");
        this.status            = SwapStatus.APPROVED;
        this.decidedBy         = decidedBy;
        this.decidedAt         = Instant.now();
        this.validationPassed  = validationPassed;
        this.validationNotes   = validationNotes;
        this.updatedAt         = Instant.now();
    }

    /**
     * Supervisor rejects.
     * Moves: PENDING | PROPOSED_ACCEPTED → REJECTED
     */
    public void reject(UUID decidedBy, String rejectionReason) {
        if (status != SwapStatus.PENDING && status != SwapStatus.PROPOSED_ACCEPTED) {
            throw new IllegalStateException(
                    "Cannot reject swap in status " + status);
        }
        this.status          = SwapStatus.REJECTED;
        this.decidedBy       = decidedBy;
        this.decidedAt       = Instant.now();
        this.rejectionReason = rejectionReason;
        this.updatedAt       = Instant.now();
    }

    /**
     * Requesting guard cancels before supervisor acts.
     * Moves: PENDING | PROPOSED_ACCEPTED → CANCELLED
     */
    public void cancel() {
        if (status != SwapStatus.PENDING && status != SwapStatus.PROPOSED_ACCEPTED) {
            throw new IllegalStateException(
                    "Cannot cancel swap in status " + status);
        }
        this.status    = SwapStatus.CANCELLED;
        this.updatedAt = Instant.now();
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private void assertStatus(SwapStatus expected, String action) {
        if (status != expected) {
            throw new IllegalStateException(
                    "Cannot " + action + " swap in status " + status +
                            "; expected " + expected);
        }
    }

    public boolean isPending()   { return status == SwapStatus.PENDING; }
    public boolean isApproved()  { return status == SwapStatus.APPROVED; }
    public boolean isTerminal()  {
        return status == SwapStatus.APPROVED
                || status == SwapStatus.REJECTED
                || status == SwapStatus.CANCELLED;
    }

    // ── Enum ───────────────────────────────────────────────────────────────────

    public enum SwapStatus {
        PENDING,
        PROPOSED_ACCEPTED,
        APPROVED,
        REJECTED,
        CANCELLED
    }
}
