package za.co.handyflow.platform.supplychain.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/** Append-only supplier audit record: never updated, never deleted. */
@Entity
@Table(name = "sc_supplier_events")
@Getter
@NoArgsConstructor
public class ScSupplierEvent {

    @Id UUID id;
    @Column(name = "tenant_id", nullable = false) UUID tenantId;
    @Column(name = "supplier_id", nullable = false) UUID supplierId;
    @Column(name = "event_type", nullable = false, length = 40) String eventType;
    @Column(name = "from_status", length = 20) String fromStatus;
    @Column(name = "to_status", length = 20) String toStatus;
    @Column(length = 1000) String detail;
    UUID actor;
    @Column(name = "actor_name", length = 255) String actorName;
    @Column(name = "created_at", nullable = false) Instant createdAt;

    public static ScSupplierEvent of(UUID tenantId, UUID supplierId, String eventType, String from, String to,
                                     String detail, UUID actor, String actorName) {
        ScSupplierEvent e = new ScSupplierEvent();
        e.id = UUID.randomUUID();
        e.tenantId = tenantId;
        e.supplierId = supplierId;
        e.eventType = eventType;
        e.fromStatus = from;
        e.toStatus = to;
        e.detail = detail != null && detail.length() > 1000 ? detail.substring(0, 1000) : detail;
        e.actor = actor;
        e.actorName = actorName;
        e.createdAt = Instant.now();
        return e;
    }
}
