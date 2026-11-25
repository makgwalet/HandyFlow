package za.co.handyflow.platform.complianceservices.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.util.UUID;

/** One row per tenant: whether client SMS reminders are on. Off until an administrator turns it on. */
@Entity(name = "ComplianceSmsSettings")
@Table(name = "compliance_sms_settings")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ComplianceSmsSettings {
    @Id
    private UUID id = UUID.randomUUID();
    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;
    @Column(nullable = false)
    private boolean enabled;
    @Column(name = "updated_by")
    private UUID updatedBy;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
    @Version
    private Long version;

    public static ComplianceSmsSettings blank(TenantId tenantId) {
        ComplianceSmsSettings s = new ComplianceSmsSettings();
        s.tenantId = tenantId;
        s.updatedAt = Instant.now();
        return s;
    }

    public void setEnabled(boolean enabled, UUID by) {
        this.enabled = enabled;
        this.updatedBy = by;
        this.updatedAt = Instant.now();
    }
}
