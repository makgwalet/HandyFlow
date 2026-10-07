// security/domain/model/GuardComplaintEvent.java
package za.co.handyflow.platform.security.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.util.UUID;

/** One line of a complaint's timeline: who did what, when. Append-only. */
@Entity
@Table(name = "security_guard_complaint_events")
@Getter
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class GuardComplaintEvent {

    @Id
    private UUID id = UUID.randomUUID();

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;

    @Column(name = "complaint_id", nullable = false)
    private UUID complaintId;

    @Column(name = "event_type", nullable = false, length = 40)
    private String eventType;

    @Column(name = "to_status", length = 30)
    private String toStatus;

    @Column(columnDefinition = "text")
    private String note;

    @Column(name = "by_name", length = 200)
    private String byName;

    @Column(name = "by_user")
    private UUID byUser;

    @Column(nullable = false)
    private Instant at;

    public static GuardComplaintEvent of(TenantId tenantId, UUID complaintId, String type, String toStatus, String note,
                                         UUID byUser, String byName) {
        GuardComplaintEvent e = new GuardComplaintEvent();
        e.tenantId = tenantId;
        e.complaintId = complaintId;
        e.eventType = type;
        e.toStatus = toStatus;
        e.note = note == null || note.isBlank() ? null : note.trim();
        e.byUser = byUser;
        e.byName = byName;
        e.at = Instant.now();
        return e;
    }
}
