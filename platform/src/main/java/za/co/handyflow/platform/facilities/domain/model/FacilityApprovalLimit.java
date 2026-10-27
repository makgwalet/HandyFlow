package za.co.handyflow.platform.facilities.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.UUID;

/** The most one person may approve on a single purchase order. */
@Entity
@Table(name = "facility_approval_limits")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FacilityApprovalLimit {

    @Id
    private UUID id = UUID.randomUUID();

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "max_order_value", nullable = false, precision = 15, scale = 2)
    private BigDecimal maxOrderValue;

    @Column(name = "set_by_user_id")
    private UUID setByUserId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public static FacilityApprovalLimit create(TenantId tenantId, UUID userId, BigDecimal max, UUID setBy) {
        FacilityApprovalLimit l = new FacilityApprovalLimit();
        l.tenantId = tenantId;
        l.userId = userId;
        l.createdAt = Instant.now();
        l.change(max, setBy);
        return l;
    }

    public void change(BigDecimal max, UUID setBy) {
        if (max == null || max.signum() < 0) throw new IllegalArgumentException("The limit must not be negative");
        this.maxOrderValue = max.setScale(2, RoundingMode.HALF_UP);
        this.setByUserId = setBy;
        this.updatedAt = Instant.now();
    }
}
