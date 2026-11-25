package za.co.handyflow.platform.complianceservices.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.util.UUID;

/** One row per tenant: whether the daily digest is on, and how much it lists. Off until an administrator turns it on. */
@Entity(name = "ComplianceReminderSettings")
@Table(name = "compliance_reminder_settings")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ComplianceReminderSettings {
    @Id
    private UUID id = UUID.randomUUID();
    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;
    @Column(nullable = false)
    private boolean enabled;
    @Column(name = "min_severity", nullable = false)
    private String minSeverity = "HIGH";
    @Column(name = "max_items", nullable = false)
    private int maxItems = 25;
    @Column(name = "updated_by")
    private UUID updatedBy;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
    @Version
    private Long version;

    public static ComplianceReminderSettings blank(TenantId tenantId) {
        ComplianceReminderSettings s = new ComplianceReminderSettings();
        s.tenantId = tenantId;
        s.updatedAt = Instant.now();
        return s;
    }

    /** Switching on needs someone to send to. */
    public void apply(boolean enabled, String minSeverity, int maxItems, int recipientCount, UUID by) {
        if (enabled && recipientCount == 0) throw new IllegalStateException("Choose at least one person to send the digest to before switching it on");
        if (recipientCount > ReminderRules.MAX_RECIPIENTS) throw new IllegalArgumentException("A digest can go to at most " + ReminderRules.MAX_RECIPIENTS + " people");
        this.enabled = enabled;
        this.minSeverity = ReminderRules.severity(minSeverity);
        this.maxItems = ReminderRules.maxItems(maxItems);
        this.updatedBy = by;
        this.updatedAt = Instant.now();
    }
}
