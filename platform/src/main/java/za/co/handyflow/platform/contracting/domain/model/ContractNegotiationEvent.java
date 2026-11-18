package za.co.handyflow.platform.contracting.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/** Audit record of what happened in a negotiation, by whom. Written once, never changed. */
@Entity
@Table(name = "contract_negotiation_events")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ContractNegotiationEvent {
    @Id private UUID id;
    @Column(name = "tenant_id") private UUID tenantId;
    @Column(name = "contract_id") private UUID contractId;
    @Column(name = "negotiation_id") private UUID negotiationId;
    @Column(name = "actor_type") private String actorType;
    @Column(name = "actor_user_id") private UUID actorUserId;
    @Column(name = "party_id") private UUID partyId;
    @Column(name = "event_type") private String eventType;
    private String detail;
    @Column(name = "ip_address") private String ipAddress;
    @Column(name = "created_at") private Instant createdAt;

    public static ContractNegotiationEvent of(UUID tenantId, UUID contractId, UUID negotiationId, String actorType, UUID userId, UUID partyId,
                                              String eventType, String detail, String ip) {
        ContractNegotiationEvent e = new ContractNegotiationEvent();
        e.id = UUID.randomUUID();
        e.tenantId = tenantId; e.contractId = contractId; e.negotiationId = negotiationId;
        e.actorType = actorType; e.actorUserId = userId; e.partyId = partyId;
        e.eventType = eventType;
        e.detail = detail == null ? null : detail.length() > 1000 ? detail.substring(0, 1000) : detail;
        e.ipAddress = ip;
        e.createdAt = Instant.now();
        return e;
    }
}
