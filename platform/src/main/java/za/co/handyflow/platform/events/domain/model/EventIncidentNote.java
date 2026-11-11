package za.co.handyflow.platform.events.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.util.UUID;

/**
 * One line in an incident's timeline. Append-only: there is no edit and no delete. Besides free-text
 * notes, every assignment, escalation and status change writes one, so the timeline alone tells the
 * whole story of how an incident was handled — which is what you need when it is reviewed afterwards.
 */
@Entity
@Table(name = "event_incident_notes")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EventIncidentNote {

    public static final String NOTE = "NOTE";
    public static final String ASSIGNMENT = "ASSIGNMENT";
    public static final String ESCALATION = "ESCALATION";
    public static final String STATUS = "STATUS";

    @Id
    private UUID id;

    @Column(name = "tenant_id", nullable = false)   private UUID tenantId;
    @Column(name = "event_id", nullable = false)    private UUID eventId;
    @Column(name = "incident_id", nullable = false) private UUID incidentId;
    @Column(name = "kind", nullable = false)        private String kind;
    @Column(name = "note", nullable = false)        private String note;
    @Column(name = "author_name")                   private String authorName;
    @Column(name = "author_user_id")                private UUID authorUserId;
    @Column(name = "created_at", nullable = false)  private Instant createdAt;

    public static EventIncidentNote of(TenantId tenantId, UUID eventId, UUID incidentId, String kind,
                                       String note, UUID authorUserId, String authorName) {
        if (note == null || note.isBlank())
            throw new IllegalArgumentException("A note can't be empty");
        EventIncidentNote n = new EventIncidentNote();
        n.id           = UUID.randomUUID();
        n.tenantId     = tenantId.getValue();
        n.eventId      = eventId;
        n.incidentId   = incidentId;
        n.kind         = kind;
        n.note         = note.trim();
        n.authorUserId = authorUserId;
        n.authorName   = authorName;
        n.createdAt    = Instant.now();
        return n;
    }
}