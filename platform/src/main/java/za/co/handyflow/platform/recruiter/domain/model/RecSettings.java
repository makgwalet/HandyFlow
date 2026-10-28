package za.co.handyflow.platform.recruiter.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/** What a company has chosen for recruiting: whether jobs and offers need approval, and how long unsuccessful applicants' details are kept. */
@Entity
@Table(name = "rec_settings")
@Getter
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class RecSettings {

    @Id @Column(name = "tenant_id") private UUID tenantId;
    @Column(name = "require_job_approval",   nullable = false) private boolean requireJobApproval = false;
    @Column(name = "require_offer_approval", nullable = false) private boolean requireOfferApproval = false;
    @Column(name = "retention_months",       nullable = false) private int retentionMonths = RecruiterRetentionRules.DEFAULT_MONTHS;
    @Column(name = "privacy_notice_url", length = 500) private String privacyNoticeUrl;
    @Column(name = "updated_by") private UUID updatedBy;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt = Instant.now();

    public static RecSettings defaults(UUID tenantId) {
        RecSettings s = new RecSettings();
        s.tenantId = tenantId;
        return s;
    }

    public void update(boolean requireJobApproval, boolean requireOfferApproval, int retentionMonths, String privacyNoticeUrl, UUID by) {
        this.privacyNoticeUrl = privacyNoticeUrl;
        update(requireJobApproval, requireOfferApproval, retentionMonths, by);
    }

    public void update(boolean requireJobApproval, boolean requireOfferApproval, int retentionMonths, UUID by) {
        this.requireJobApproval = requireJobApproval;
        this.requireOfferApproval = requireOfferApproval;
        this.retentionMonths = retentionMonths;
        this.updatedBy = by;
        this.updatedAt = Instant.now();
    }
}
