package za.co.handyflow.platform.earthmoving.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.util.UUID;

/** One person on one level of a rule: level 1 is told first, level 2 is told if nobody acknowledges in time. */
@Entity
@Table(name = "earthmoving_alert_recipients")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EarthAlertRecipient {
    @Id
    private UUID id = UUID.randomUUID();

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;

    @Column(name = "rule_id", nullable = false)
    private UUID ruleId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(nullable = false)
    private int level;

    public static EarthAlertRecipient of(TenantId tenantId, UUID ruleId, UUID userId, int level) {
        EarthAlertRecipient r = new EarthAlertRecipient();
        r.tenantId = tenantId;
        r.ruleId = ruleId;
        r.userId = userId;
        r.level = level;
        return r;
    }
}
