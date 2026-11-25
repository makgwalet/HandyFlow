package za.co.handyflow.platform.complianceservices.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** One row per tenant: whether a second person must activate an agreement, and from what quoted total. Off until an administrator switches it on. */
@Entity(name = "ComplianceAgreementSettings")
@Table(name = "compliance_agreement_settings")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ComplianceAgreementSettings {
    @Id
    private UUID id = UUID.randomUUID();
    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;
    @Column(name = "require_second_person", nullable = false)
    private boolean requireSecondPerson;
    @Column(name = "min_value")
    private BigDecimal minValue;
    @Column(name = "updated_by")
    private UUID updatedBy;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
    @Version
    private Long version;

    public static ComplianceAgreementSettings blank(TenantId tenantId) {
        ComplianceAgreementSettings s = new ComplianceAgreementSettings();
        s.tenantId = tenantId;
        s.updatedAt = Instant.now();
        return s;
    }

    public void apply(boolean requireSecondPerson, BigDecimal minValue, UUID by) {
        if (minValue != null && minValue.signum() <= 0) throw new IllegalArgumentException("The amount must be more than zero");
        this.requireSecondPerson = requireSecondPerson;
        this.minValue = minValue == null ? null : minValue.setScale(2, java.math.RoundingMode.HALF_UP);
        this.updatedBy = by;
        this.updatedAt = Instant.now();
    }
}
