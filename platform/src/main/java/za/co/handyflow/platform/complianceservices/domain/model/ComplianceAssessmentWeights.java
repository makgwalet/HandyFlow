package za.co.handyflow.platform.complianceservices.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** One tenant's scoring weights by risk level. No row means the defaults. */
@Entity
@Table(name = "compliance_assessment_weights")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ComplianceAssessmentWeights {

    @Id
    private UUID id = UUID.randomUUID();

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;

    @Column(name = "w_low", nullable = false)
    private int low = 1;
    @Column(name = "w_medium", nullable = false)
    private int medium = 2;
    @Column(name = "w_high", nullable = false)
    private int high = 4;
    @Column(name = "w_critical", nullable = false)
    private int critical = 8;

    @Column(name = "updated_by")
    private UUID updatedBy;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    @Version
    private Long version;

    public static ComplianceAssessmentWeights forTenant(TenantId tenantId) {
        ComplianceAssessmentWeights w = new ComplianceAssessmentWeights();
        w.tenantId = tenantId;
        return w;
    }

    public AssessmentRules.Weights asWeights() { return new AssessmentRules.Weights(low, medium, high, critical); }

    public void set(AssessmentRules.Weights w, UUID by) {
        this.low = w.low();
        this.medium = w.medium();
        this.high = w.high();
        this.critical = w.critical();
        this.updatedBy = by;
        this.updatedAt = Instant.now();
    }
}
