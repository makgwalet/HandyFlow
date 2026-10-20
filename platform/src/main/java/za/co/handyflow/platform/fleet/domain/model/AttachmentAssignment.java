package za.co.handyflow.platform.fleet.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** One stay of an attachment on one machine. Open while {@code detachedAt} is null. */
@Entity
@Table(name = "fleet_attachment_assignments")
@Getter
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class AttachmentAssignment {

    @Id
    private UUID id = UUID.randomUUID();

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;

    @Column(name = "attachment_id", nullable = false)
    private UUID attachmentId;

    @Column(name = "vehicle_id", nullable = false)
    private UUID vehicleId;

    @Column(name = "attached_at", nullable = false)
    private Instant attachedAt;

    @Column(name = "attached_engine_hours", nullable = false, precision = 10, scale = 1)
    private BigDecimal attachedEngineHours;

    @Column(name = "detached_at")
    private Instant detachedAt;

    @Column(name = "detached_engine_hours", precision = 10, scale = 1)
    private BigDecimal detachedEngineHours;

    public static AttachmentAssignment open(TenantId tenantId, UUID attachmentId, UUID vehicleId,
                                            Instant attachedAt, BigDecimal engineHours) {
        AttachmentAssignment a = new AttachmentAssignment();
        a.tenantId = tenantId;
        a.attachmentId = attachmentId;
        a.vehicleId = vehicleId;
        a.attachedAt = attachedAt;
        a.attachedEngineHours = engineHours;
        return a;
    }

    public void close(Instant at, BigDecimal engineHours) {
        if (detachedAt != null) throw new IllegalStateException("This assignment is already closed");
        if (at.isBefore(attachedAt)) throw new IllegalArgumentException("Detaching cannot be before attaching");
        this.detachedAt = at;
        this.detachedEngineHours = engineHours;
    }

    /** Hours worked during this stay; null while it is still open. */
    public BigDecimal getHours() {
        return detachedEngineHours != null ? detachedEngineHours.subtract(attachedEngineHours) : null;
    }
}