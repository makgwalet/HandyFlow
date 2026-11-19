package za.co.handyflow.platform.contracting.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/** Append-only record of every change to authority (the database refuses updates and deletes). */
@Entity
@Table(name = "contract_authority_events")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ContractAuthorityEvent {
    @Id private UUID id;
    @Column(name = "tenant_id") private UUID tenantId;
    @Column(name = "grant_id") private UUID grantId;
    @Column(name = "event_type") private String eventType;
    @Column(name = "actor_user_id") private UUID actorUserId;
    private String detail;
    @Column(name = "created_at") private Instant createdAt;

    public static ContractAuthorityEvent of(UUID tenantId, UUID grantId, String type, UUID actor, String detail) {
        ContractAuthorityEvent e = new ContractAuthorityEvent();
        e.id = UUID.randomUUID(); e.tenantId = tenantId; e.grantId = grantId; e.eventType = type; e.actorUserId = actor;
        e.detail = detail == null ? null : detail.length() > 1000 ? detail.substring(0, 1000) : detail;
        e.createdAt = Instant.now();
        return e;
    }
}
