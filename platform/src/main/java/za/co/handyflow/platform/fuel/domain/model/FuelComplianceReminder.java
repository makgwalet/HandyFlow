package za.co.handyflow.platform.fuel.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** Records that a reminder for one expiry date of one item has gone out, so the daily sweep never repeats it. A renewal changes the date, so it starts a fresh set. */
@Entity
@Table(name = "fuel_compliance_reminders")
@Getter
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class FuelComplianceReminder {

    @Id
    private UUID id = UUID.randomUUID();

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;

    @Column(name = "item_id", nullable = false, updatable = false) private UUID itemId;
    @Column(name = "expires_on", nullable = false, updatable = false) private LocalDate expiresOn;
    @Column(nullable = false, updatable = false, length = 10) private String stage;
    @Column(name = "sent_at", nullable = false, updatable = false) private Instant sentAt;

    public static FuelComplianceReminder of(TenantId tenantId, UUID itemId, LocalDate expiresOn, String stage) {
        FuelComplianceReminder r = new FuelComplianceReminder();
        r.tenantId = tenantId; r.itemId = itemId; r.expiresOn = expiresOn; r.stage = stage; r.sentAt = Instant.now();
        return r;
    }
}
