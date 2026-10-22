package za.co.handyflow.platform.hr.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** One colleague's feedback about an employee in a review. Who gave it is known to HR only. */
@Entity
@Table(name = "hr_perf_feedback")
@Getter
@NoArgsConstructor
public class HrPerfFeedback {

    @Id UUID id;
    @Column(name = "tenant_id") UUID tenantId;
    @Column(name = "review_id") UUID reviewId;
    @Column(name = "provider_id") UUID providerId;
    String relationship;
    String status = "REQUESTED";
    @JdbcTypeCode(SqlTypes.SMALLINT) Integer rating;
    String strengths;
    String improvements;
    @Column(name = "decline_reason") String declineReason;
    @Column(name = "requested_by") UUID requestedBy;
    @Column(name = "requested_at") Instant requestedAt;
    @Column(name = "responded_at") Instant respondedAt;
    @Version long version;

    public static HrPerfFeedback request(UUID tenantId, UUID reviewId, UUID providerId, String relationship, UUID by) {
        HrPerfFeedback f = new HrPerfFeedback();
        f.id = UUID.randomUUID();
        f.tenantId = tenantId;
        f.reviewId = reviewId;
        f.providerId = providerId;
        f.relationship = relationship;
        f.requestedBy = by;
        f.requestedAt = Instant.now();
        return f;
    }

    public void submit(int rating, String strengths, String improvements) {
        this.status = "SUBMITTED";
        this.rating = rating;
        this.strengths = strengths == null || strengths.isBlank() ? null : strengths.trim();
        this.improvements = improvements == null || improvements.isBlank() ? null : improvements.trim();
        this.respondedAt = Instant.now();
    }

    public void decline(String reason) {
        this.status = "DECLINED";
        this.declineReason = reason == null || reason.isBlank() ? null : reason.trim();
        this.respondedAt = Instant.now();
    }
}
