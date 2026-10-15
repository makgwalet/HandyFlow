package za.co.handyflow.platform.earthmoving.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.util.UUID;

/** Who an alert was sent to, at which level, and whether it went out. */
@Entity
@Table(name = "earthmoving_alert_deliveries")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EarthAlertDelivery {
    @Id
    private UUID id = UUID.randomUUID();

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;

    @Column(name = "alert_id", nullable = false)
    private UUID alertId;

    @Column(name = "user_id")
    private UUID userId;

    @Column(nullable = false)
    private int level;

    @Column(nullable = false)
    private String status;

    private String detail;

    @Column(name = "sent_at", nullable = false)
    private Instant sentAt;

    public static EarthAlertDelivery of(TenantId tenantId, UUID alertId, UUID userId, int level, boolean sent, String detail) {
        EarthAlertDelivery d = new EarthAlertDelivery();
        d.tenantId = tenantId;
        d.alertId = alertId;
        d.userId = userId;
        d.level = level;
        d.status = sent ? "SENT" : "FAILED";
        d.detail = detail;
        d.sentAt = Instant.now();
        return d;
    }
}
