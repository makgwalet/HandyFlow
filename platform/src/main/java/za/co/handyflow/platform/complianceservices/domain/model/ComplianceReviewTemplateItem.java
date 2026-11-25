package za.co.handyflow.platform.complianceservices.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** One check on a template. A critical check that fails means the work cannot be approved. */
@Entity(name = "ComplianceReviewTemplateItem")
@Table(name = "compliance_review_template_items")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ComplianceReviewTemplateItem {
    @Id
    private UUID id = UUID.randomUUID();
    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;
    @Column(name = "template_id", nullable = false)
    private UUID templateId;
    @Column(nullable = false)
    private int position;
    @Column(nullable = false)
    private String text;
    @Column(nullable = false)
    private boolean critical;

    public static ComplianceReviewTemplateItem of(TenantId tenantId, UUID templateId, int position, String text, boolean critical) {
        ComplianceReviewTemplateItem i = new ComplianceReviewTemplateItem();
        i.tenantId = tenantId;
        i.templateId = templateId;
        i.position = position;
        i.text = QualityRules.text(text, "Each check", 500, true);
        i.critical = critical;
        return i;
    }
}
