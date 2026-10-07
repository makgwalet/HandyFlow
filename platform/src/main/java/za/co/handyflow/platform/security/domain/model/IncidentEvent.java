// security/domain/model/IncidentEvent.java
package za.co.handyflow.platform.security.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.util.UUID;

/** One line of an incident's timeline: who did what, when. Append-only. */
@Entity
@Table(name = "security_incident_events")
@Getter
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class IncidentEvent {

    @Id
    private UUID id = UUID.randomUUID();

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;

    @Column(name = "incident_id", nullable = false)
    private UUID incidentId;

    @Column(name = "event_type", nullable = false, length = 40)
    private String eventType;

    @Column(name = "to_status", length = 30)
    private String toStatus;

    @Column(columnDefinition = "text")
    private String note;

    @Column(name = "by_user")
    private UUID byUser;

    @Column(name = "by_name", length = 200)
    private String byName;

    @Column(nullable = false)
    private Instant at;

    public static IncidentEvent of(TenantId tenantId, UUID incidentId, String type, String toStatus, String note, UUID byUser, String byName) {
        IncidentEvent e = new IncidentEvent();
        e.tenantId = tenantId; e.incidentId = incidentId; e.eventType = type; e.toStatus = toStatus;
        e.note = note == null || note.isBlank() ? null : note.trim();
        e.byUser = byUser; e.byName = byName; e.at = Instant.now();
        return e;
    }
}
