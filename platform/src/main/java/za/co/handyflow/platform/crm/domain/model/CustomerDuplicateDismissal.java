package za.co.handyflow.platform.crm.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.util.UUID;

/** Two customers a person decided are not duplicates of each other. */
@Entity
@Table(name = "customer_duplicate_dismissals")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CustomerDuplicateDismissal {

    @Id
    private UUID id = UUID.randomUUID();

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;

    @Column(name = "pair_key", nullable = false, length = 80)
    private String pairKey;

    @Column(name = "customer_a", nullable = false)
    private UUID customerA;

    @Column(name = "customer_b", nullable = false)
    private UUID customerB;

    @Column(name = "dismissed_by")
    private UUID dismissedBy;

    @Column(name = "dismissed_at", nullable = false)
    private Instant dismissedAt;

    public static CustomerDuplicateDismissal of(TenantId tenantId, String pairKey, UUID a, UUID b, UUID by) {
        var d = new CustomerDuplicateDismissal();
        d.tenantId = tenantId;
        d.pairKey = pairKey;
        d.customerA = a;
        d.customerB = b;
        d.dismissedBy = by;
        d.dismissedAt = Instant.now();
        return d;
    }
}
