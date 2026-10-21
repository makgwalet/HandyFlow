package za.co.handyflow.platform.crm.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** What one salesperson is aiming to win in one month. */
@Entity
@Table(name = "crm_sales_targets")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SalesTarget {

    @Id
    private UUID id = UUID.randomUUID();

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;

    @Column(name = "owner_id", nullable = false)
    private UUID ownerId;

    /** 'YYYY-MM'. */
    @Column(name = "period", nullable = false, length = 7)
    private String period;

    @Column(name = "amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal amount;

    @Column(name = "set_by")
    private UUID setBy;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public static SalesTarget create(TenantId tenantId, UUID ownerId, String period, BigDecimal amount, UUID by) {
        var t = new SalesTarget();
        t.tenantId = tenantId;
        t.ownerId = ownerId;
        t.period = period;
        t.amount = amount;
        t.setBy = by;
        t.updatedAt = Instant.now();
        return t;
    }

    public void changeAmount(BigDecimal amount, UUID by) {
        this.amount = amount;
        this.setBy = by;
        this.updatedAt = Instant.now();
    }
}
