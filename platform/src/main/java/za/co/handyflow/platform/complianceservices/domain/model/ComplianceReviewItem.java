package za.co.handyflow.platform.complianceservices.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** One check on one review, with the wording it had when the review started. */
@Entity(name = "ComplianceReviewItem")
@Table(name = "compliance_review_items")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ComplianceReviewItem {
    @Id
    private UUID id = UUID.randomUUID();
    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;
    @Column(name = "review_id", nullable = false)
    private UUID reviewId;
    @Column(nullable = false)
    private int position;
    @Column(nullable = false)
    private String text;
    @Column(nullable = false)
    private boolean critical;
    @Column(nullable = false)
    private String result = "PENDING";
    @Column(columnDefinition = "TEXT")
    private String note;
    @Version
    private Long version;

    public static ComplianceReviewItem copyOf(TenantId tenantId, UUID reviewId, int position, String text, boolean critical) {
        ComplianceReviewItem i = new ComplianceReviewItem();
        i.tenantId = tenantId;
        i.reviewId = reviewId;
        i.position = position;
        i.text = text;
        i.critical = critical;
        return i;
    }

    public void mark(String result, String note) {
        String r = QualityRules.result(result);
        this.note = QualityRules.itemNote(r, note);
        this.result = r;
    }

    public QualityRules.Line line() { return new QualityRules.Line(position, critical, result); }
}
