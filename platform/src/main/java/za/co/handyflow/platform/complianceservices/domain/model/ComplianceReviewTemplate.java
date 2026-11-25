package za.co.handyflow.platform.complianceservices.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** A reusable checklist for one kind of work. Its items are copied onto each review, so editing it never changes a review already done. */
@Entity(name = "ComplianceReviewTemplate")
@Table(name = "compliance_review_templates")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ComplianceReviewTemplate {
    @Id
    private UUID id = UUID.randomUUID();
    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;
    @Column(nullable = false)
    private String name;
    @Column(name = "subject_type", nullable = false)
    private String subjectType;
    @Column(columnDefinition = "TEXT")
    private String description;
    @Column(nullable = false)
    private boolean active = true;
    @Column(name = "created_by")
    private UUID createdBy;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
    @Version
    private Long version;

    public static ComplianceReviewTemplate create(TenantId tenantId, String name, String subjectType, String description, UUID by) {
        ComplianceReviewTemplate t = new ComplianceReviewTemplate();
        t.tenantId = tenantId;
        t.createdBy = by;
        t.createdAt = Instant.now();
        t.edit(name, subjectType, description);
        return t;
    }

    public void edit(String name, String subjectType, String description) {
        this.name = QualityRules.text(name, "Name", 200, true);
        this.subjectType = QualityRules.subjectType(subjectType);
        this.description = QualityRules.text(description, "Description", 2000, false);
    }

    public void setActive(boolean active) { this.active = active; }

    /** Checks a list of item texts, which must have at least one and not too many. */
    public static void checkItems(List<String> texts) {
        if (texts == null || texts.isEmpty()) throw new IllegalArgumentException("A checklist needs at least one check");
        if (texts.size() > QualityRules.MAX_ITEMS) throw new IllegalArgumentException("A checklist has at most " + QualityRules.MAX_ITEMS + " checks");
        texts.forEach(t -> QualityRules.text(t, "Each check", 500, true));
    }
}
