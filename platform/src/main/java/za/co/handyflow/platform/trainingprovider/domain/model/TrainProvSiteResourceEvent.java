package za.co.handyflow.platform.trainingprovider.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** One entry in a resource's history. Written once, never changed. */
@Entity(name = "TrainProvSiteResourceEvent")
@Table(name = "trainprov_site_resource_events")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TrainProvSiteResourceEvent {
    @Id
    private UUID id = UUID.randomUUID();
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;
    @Column(name = "resource_id", nullable = false, updatable = false)
    private UUID resourceId;
    @Column(name = "event_type", nullable = false, updatable = false)
    private String eventType;
    @Column(name = "from_status", updatable = false)
    private String fromStatus;
    @Column(name = "to_status", updatable = false)
    private String toStatus;
    @Column(name = "from_site_id", updatable = false)
    private UUID fromSiteId;
    @Column(name = "to_site_id", updatable = false)
    private UUID toSiteId;
    @Column(updatable = false)
    private String note;
    @Column(name = "occurred_on", nullable = false, updatable = false)
    private LocalDate occurredOn;
    @Column(name = "created_by", nullable = false, updatable = false)
    private UUID createdBy;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public static TrainProvSiteResourceEvent of(TenantId t, UUID resourceId, String type, String fromStatus, String toStatus, UUID fromSite, UUID toSite, String note,
                                                LocalDate on, UUID by) {
        TrainProvSiteResourceEvent e = new TrainProvSiteResourceEvent();
        e.tenantId = t.getValue();
        e.resourceId = resourceId;
        e.eventType = type;
        e.fromStatus = fromStatus;
        e.toStatus = toStatus;
        e.fromSiteId = fromSite;
        e.toSiteId = toSite;
        e.note = note == null || note.isBlank() ? null : note.trim();
        e.occurredOn = on;
        e.createdBy = by;
        e.createdAt = Instant.now();
        return e;
    }
}
