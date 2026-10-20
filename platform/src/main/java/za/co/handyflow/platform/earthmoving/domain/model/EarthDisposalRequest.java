package za.co.handyflow.platform.earthmoving.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * A request to take a machine out of the fleet. REQUESTED → APPROVED (or REJECTED) → COMPLETED, or CANCELLED while still live.
 * Completing it retires the machine; nothing else does.
 */
@Entity
@Table(name = "earthmoving_disposal_requests")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EarthDisposalRequest {

    @Id
    private UUID id = UUID.randomUUID();

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;

    @Column(name = "asset_id", nullable = false)
    private UUID assetId;

    @Column(nullable = false)
    private String reason;

    @Column(nullable = false)
    private String status = DisposalRules.REQUESTED;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String details;

    private String counterparty;

    @Column(precision = 15, scale = 2)
    private BigDecimal proceeds;

    @Column(name = "requested_by", nullable = false)
    private UUID requestedBy;

    @Column(name = "requested_at", nullable = false)
    private Instant requestedAt;

    @Column(name = "decided_by")
    private UUID decidedBy;

    @Column(name = "decided_at")
    private Instant decidedAt;

    @Column(name = "decision_note", columnDefinition = "TEXT")
    private String decisionNote;

    @Column(name = "completed_by")
    private UUID completedBy;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "cancelled_by")
    private UUID cancelledBy;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @Column(name = "cancel_reason", columnDefinition = "TEXT")
    private String cancelReason;

    public static EarthDisposalRequest request(TenantId tenantId, UUID assetId, String reason, String details, String counterparty,
                                               BigDecimal proceeds, UUID actor) {
        EarthDisposalRequest r = new EarthDisposalRequest();
        r.tenantId = tenantId;
        r.assetId = assetId;
        r.reason = DisposalRules.reason(reason);
        r.details = DisposalRules.details(details);
        r.counterparty = DisposalRules.counterparty(r.reason, counterparty);
        r.proceeds = DisposalRules.proceeds(proceeds);
        r.requestedBy = actor;
        r.requestedAt = Instant.now();
        return r;
    }

    public boolean isLive() { return DisposalRules.LIVE.contains(status); }

    public void approve(String note, UUID actor) {
        if (!DisposalRules.REQUESTED.equals(status)) throw new IllegalStateException("Only a request that is waiting can be approved. This one is " + status.toLowerCase() + ".");
        DisposalRules.requireDifferentPerson(requestedBy, actor);
        this.status = DisposalRules.APPROVED;
        decide(DisposalRules.optionalNote(note), actor);
    }

    public void reject(String note, UUID actor) {
        if (!DisposalRules.REQUESTED.equals(status)) throw new IllegalStateException("Only a request that is waiting can be rejected. This one is " + status.toLowerCase() + ".");
        DisposalRules.requireDifferentPerson(requestedBy, actor);
        String why = DisposalRules.note(note, "rejecting this request");
        this.status = DisposalRules.REJECTED;
        decide(why, actor);
    }

    public void complete(UUID actor) {
        if (!DisposalRules.APPROVED.equals(status)) throw new IllegalStateException("Only an approved request can be completed. This one is " + status.toLowerCase() + ".");
        this.status = DisposalRules.COMPLETED;
        this.completedBy = actor;
        this.completedAt = Instant.now();
    }

    public void cancel(String reason, UUID actor) {
        if (!isLive()) throw new IllegalStateException("This request is " + status.toLowerCase() + " and can no longer be cancelled.");
        this.cancelReason = DisposalRules.note(reason, "cancelling this request");
        this.status = DisposalRules.CANCELLED;
        this.cancelledBy = actor;
        this.cancelledAt = Instant.now();
    }

    private void decide(String note, UUID actor) {
        this.decisionNote = note;
        this.decidedBy = actor;
        this.decidedAt = Instant.now();
    }
}
