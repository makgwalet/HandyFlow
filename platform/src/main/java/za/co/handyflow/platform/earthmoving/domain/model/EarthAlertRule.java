package za.co.handyflow.platform.earthmoving.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.util.UUID;

/** Who is told about one kind of alert for one company. The recipients are separate rows (EarthAlertRecipient). */
@Entity
@Table(name = "earthmoving_alert_rules")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EarthAlertRule {
    @Id
    private UUID id = UUID.randomUUID();

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;

    @Column(name = "alert_type", nullable = false)
    private String alertType;

    @Column(nullable = false)
    private boolean enabled = true;

    @Column(name = "escalate_after_minutes")
    private Integer escalateAfterMinutes;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public static EarthAlertRule create(TenantId tenantId, String alertType) {
        EarthAlertRule r = new EarthAlertRule();
        r.tenantId = tenantId;
        r.alertType = alertType;
        r.createdAt = Instant.now();
        r.updatedAt = r.createdAt;
        return r;
    }

    public void configure(boolean enabled, Integer escalateAfterMinutes) {
        this.enabled = enabled;
        this.escalateAfterMinutes = escalateAfterMinutes;
        this.updatedAt = Instant.now();
    }
}
