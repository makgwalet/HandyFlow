package za.co.handyflow.platform.fuel.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.util.UUID;

/** A user a company has chosen to tell about one kind of Fuel alert, in addition to its administrators. */
@Entity
@Table(name = "fuel_alert_recipients")
@Getter
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class FuelAlertRecipient {

    @Id
    private UUID id = UUID.randomUUID();

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;

    @Column(nullable = false, updatable = false, length = 30)
    private String topic;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "created_by", updatable = false)
    private UUID createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public static FuelAlertRecipient of(TenantId tenantId, String topic, UUID userId, UUID by) {
        FuelAlertRecipient r = new FuelAlertRecipient();
        r.tenantId = tenantId; r.topic = topic; r.userId = userId; r.createdBy = by;
        return r;
    }
}
