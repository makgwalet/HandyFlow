package za.co.handyflow.platform.contracting.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/** Append-only record of what happened to an approval request. */
@Entity
@Table(name = "contract_approval_events")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ContractApprovalEvent {
    @Id private UUID id;
    @Column(name = "tenant_id") private UUID tenantId;
    @Column(name = "approval_id") private UUID approvalId;
    @Column(name = "event_type") private String eventType;
    @Column(name = "actor_user_id") private UUID actorUserId;
    private String detail;
    @Column(name = "created_at") private Instant createdAt;

    public static ContractApprovalEvent of(UUID tenantId, UUID approvalId, String type, UUID actor, String detail) {
        ContractApprovalEvent e = new ContractApprovalEvent();
        e.id = UUID.randomUUID(); e.tenantId = tenantId; e.approvalId = approvalId;
        e.eventType = type; e.actorUserId = actor;
        e.detail = detail == null ? null : detail.length() > 1000 ? detail.substring(0, 1000) : detail;
        e.createdAt = Instant.now();
        return e;
    }
}
