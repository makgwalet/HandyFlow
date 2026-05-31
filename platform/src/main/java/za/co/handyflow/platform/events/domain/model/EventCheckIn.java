package za.co.handyflow.platform.events.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "event_check_ins")
@Getter
@NoArgsConstructor
public class EventCheckIn {

    @Id UUID id;
    @Column(name = "tenant_id")  UUID tenantId;
    @Column(name = "event_id")   UUID eventId;
    @Column(name = "guest_id")   UUID guestId;
    @Column(name = "scanned_at") Instant scannedAt;
    @Column(name = "scanned_by") UUID scannedBy;
    @Column(name = "scan_device") String scanDevice;
    String location;
    String result;

    public static EventCheckIn create(TenantId tenantId, UUID eventId,
                                      UUID guestId, UUID scannedBy,
                                      String location, String result) {
        EventCheckIn c = new EventCheckIn();
        c.id        = UUID.randomUUID();
        c.tenantId  = tenantId.getValue();
        c.eventId   = eventId;
        c.guestId   = guestId;
        c.scannedBy = scannedBy;
        c.location  = location;
        c.result    = result;
        c.scannedAt = Instant.now();
        return c;
    }
}