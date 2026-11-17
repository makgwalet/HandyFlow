package za.co.handyflow.platform.supplychain.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/** Append-only audit of budget changes and overrides — raising a limit or relaxing its mode is how controls get bypassed. */
@Entity
@Table(name = "sc_budget_events")
@Getter
@NoArgsConstructor
public class ScBudgetEvent {

    @Id UUID id;
    @Column(name = "tenant_id", nullable = false) UUID tenantId;
    @Column(name = "budget_id") UUID budgetId;
    @Column(name = "event_type", nullable = false, length = 30) String eventType;
    @Column(length = 1000) String detail;
    UUID actor;
    @Column(name = "actor_name", length = 255) String actorName;
    @Column(name = "created_at", nullable = false) Instant createdAt;

    public static ScBudgetEvent of(UUID tenantId, UUID budgetId, String type, String detail, UUID actor, String actorName) {
        ScBudgetEvent e = new ScBudgetEvent();
        e.id = UUID.randomUUID();
        e.tenantId = tenantId;
        e.budgetId = budgetId;
        e.eventType = type;
        e.detail = detail != null && detail.length() > 1000 ? detail.substring(0, 1000) : detail;
        e.actor = actor;
        e.actorName = actorName;
        e.createdAt = Instant.now();
        return e;
    }
}
