package za.co.handyflow.platform.internalaudit.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Optional, per-account/GL-segment materiality override — the product
 * owner's own Petty Cash example precisely: a relatively small amount
 * there can matter for control/fraud reasons even though it's far
 * below the engagement's overall materiality. Deliberately not a
 * required per-account configuration step — creating one is a
 * deliberate, occasional action by the auditor, not a form field
 * they're forced to fill in for every account on every engagement.
 */
@Entity
@Table(name = "audit_engagement_specific_materiality")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SpecificMateriality {

    @Id
    private UUID id = UUID.randomUUID();

    @Column(name = "tenant_id", nullable = false) private UUID tenantId;
    @Column(name = "engagement_id", nullable = false) private UUID engagementId;
    @Column(name = "account_or_gl_segment", nullable = false) private String accountOrGlSegment;
    @Column(name = "threshold", nullable = false) private BigDecimal threshold;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;

    public static SpecificMateriality create(UUID tenantId, UUID engagementId,
                                             String accountOrGlSegment, BigDecimal threshold) {
        SpecificMateriality m = new SpecificMateriality();
        m.tenantId = tenantId;
        m.engagementId = engagementId;
        m.accountOrGlSegment = accountOrGlSegment;
        m.threshold = threshold;
        m.createdAt = Instant.now();
        return m;
    }
}
