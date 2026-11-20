package za.co.handyflow.platform.legalpractice.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.util.UUID;

/** One time a barred person was turned away from a matter. Append-only. */
@Entity
@Table(name = "lp_wall_denials")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LpWallDenial {

    @Id
    private UUID id = UUID.randomUUID();

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "user_name")
    private String userName;

    @Column(name = "matter_id", nullable = false)
    private UUID matterId;

    @Column(name = "client_id")
    private UUID clientId;

    @Column(name = "wall_id", nullable = false)
    private UUID wallId;

    @Column(name = "denied_at", nullable = false)
    private Instant deniedAt;

    public static LpWallDenial of(TenantId tenantId, UUID userId, String userName, UUID matterId, UUID clientId, UUID wallId, Instant at) {
        LpWallDenial d = new LpWallDenial();
        d.tenantId = tenantId;
        d.userId = userId;
        d.userName = userName;
        d.matterId = matterId;
        d.clientId = clientId;
        d.wallId = wallId;
        d.deniedAt = at;
        return d;
    }
}
