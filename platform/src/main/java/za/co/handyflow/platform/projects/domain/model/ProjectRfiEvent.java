package za.co.handyflow.platform.projects.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * Append-only history of an RFI: created, submitted, assigned, responded (with the full answer),
 * clarification requested, closed, reopened, impact updated, change order linked, escalated, cancelled.
 */
@Entity
@Table(name = "project_rfi_events")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProjectRfiEvent {

    @Id private UUID id;
    @Column(name = "tenant_id", nullable = false)  private UUID tenantId;
    @Column(name = "project_id", nullable = false) private UUID projectId;
    @Column(name = "rfi_id", nullable = false)     private UUID rfiId;
    @Column(name = "event_type", nullable = false) private String eventType;
    @Column(name = "comment")                       private String comment;
    @Column(name = "actor_id")                      private UUID actorId;
    @Column(name = "actor_name")                    private String actorName;
    @Column(name = "occurred_at", nullable = false) private Instant occurredAt;

    public static ProjectRfiEvent record(UUID tenantId, UUID projectId, UUID rfiId, String eventType,
                                         String comment, UUID actorId, String actorName) {
        ProjectRfiEvent e = new ProjectRfiEvent();
        e.id         = UUID.randomUUID();
        e.tenantId   = tenantId;
        e.projectId  = projectId;
        e.rfiId      = rfiId;
        e.eventType  = eventType;
        e.comment    = comment;
        e.actorId    = actorId;
        e.actorName  = actorName;
        e.occurredAt = Instant.now();
        return e;
    }
}