package za.co.handyflow.platform.desk.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "desk_sla_policies")
@Getter
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class DeskSlaPolicy {

    @Id private UUID id = UUID.randomUUID();

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;

    @Column(nullable = false) private String priority;
    @Column(name = "first_response_hours", nullable = false) private int firstResponseHours;
    @Column(name = "resolution_hours",     nullable = false) private int resolutionHours;
    @Column(name = "created_at") private Instant createdAt;

    public static DeskSlaPolicy create(TenantId tenantId, String priority,
                                        int firstResponseHours, int resolutionHours) {
        DeskSlaPolicy p       = new DeskSlaPolicy();
        p.tenantId            = tenantId;
        p.priority            = priority;
        p.firstResponseHours  = firstResponseHours;
        p.resolutionHours     = resolutionHours;
        p.createdAt           = Instant.now();
        return p;
    }

    public Instant calculateDueAt(Instant from) {
        return from.plusSeconds((long) resolutionHours * 3600);
    }
}
