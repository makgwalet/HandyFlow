package za.co.handyflow.platform.hr.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/** One step in a pay run's life: who did what, from which state to which, and why. Never edited or deleted. */
@Entity
@Table(name = "hr_pay_run_events")
@Getter
@NoArgsConstructor
public class HrPayRunEvent {

    @Id UUID id;
    @Column(name = "tenant_id")   UUID tenantId;
    @Column(name = "pay_run_id")  UUID payRunId;
    String action;
    @Column(name = "from_status") String fromStatus;
    @Column(name = "to_status")   String toStatus;
    @Column(name = "actor_id")    UUID actorId;
    String note;
    @Column(name = "created_at")  Instant createdAt;

    public static HrPayRunEvent of(UUID tenantId, UUID payRunId, String action, String from, String to, UUID actor, String note) {
        HrPayRunEvent e = new HrPayRunEvent();
        e.id = UUID.randomUUID();
        e.tenantId = tenantId;
        e.payRunId = payRunId;
        e.action = action;
        e.fromStatus = from;
        e.toStatus = to;
        e.actorId = actor;
        e.note = note == null || note.isBlank() ? null : (note.trim().length() > 500 ? note.trim().substring(0, 500) : note.trim());
        e.createdAt = Instant.now();
        return e;
    }
}
