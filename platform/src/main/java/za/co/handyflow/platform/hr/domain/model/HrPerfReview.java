package za.co.handyflow.platform.hr.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** One employee's review in one cycle. Once the result is released the database refuses changes to ratings and comments (V495). */
@Entity
@Table(name = "hr_perf_reviews")
@Getter
@NoArgsConstructor
public class HrPerfReview {

    @Id UUID id;
    @Column(name = "tenant_id") UUID tenantId;
    @Column(name = "cycle_id") UUID cycleId;
    @Column(name = "employee_id") UUID employeeId;
    @Column(name = "reviewer_id") UUID reviewerId;
    @Column(name = "job_title") String jobTitle;
    String status = PerfRules.AWAITING_SELF;
    @Column(name = "self_rating") Integer selfRating;
    @Column(name = "self_comment") String selfComment;
    @Column(name = "self_at") Instant selfAt;
    @Column(name = "mgr_rating") Integer mgrRating;
    @Column(name = "mgr_comment") String mgrComment;
    @Column(name = "mgr_at") Instant mgrAt;
    @Column(name = "mgr_by") UUID mgrBy;
    @Column(name = "final_rating") Integer finalRating;
    boolean calibrated;
    @Column(name = "calibration_reason") String calibrationReason;
    @Column(name = "shared_at") Instant sharedAt;
    @Column(name = "shared_by") UUID sharedBy;
    @Column(name = "ack_at") Instant ackAt;
    @Column(name = "ack_by") UUID ackBy;
    @Column(name = "employee_comment") String employeeComment;
    @Column(name = "excluded_reason") String excludedReason;
    @Column(name = "created_at") Instant createdAt;
    @Column(name = "updated_at") Instant updatedAt;
    @Version long version;

    public static HrPerfReview of(UUID tenantId, UUID cycleId, UUID employeeId, UUID reviewerId, String jobTitle) {
        HrPerfReview r = new HrPerfReview();
        r.id = UUID.randomUUID();
        r.tenantId = tenantId;
        r.cycleId = cycleId;
        r.employeeId = employeeId;
        r.reviewerId = reviewerId;
        r.jobTitle = jobTitle;
        r.createdAt = Instant.now();
        r.updatedAt = r.createdAt;
        return r;
    }

    public void submitSelf(int rating, String comment) {
        selfRating = rating;
        selfComment = comment == null || comment.isBlank() ? null : comment.trim();
        selfAt = Instant.now();
        status = PerfRules.AWAITING_MANAGER;
        touch();
    }

    public void submitManager(int rating, String comment, UUID byUser) {
        mgrRating = rating;
        mgrComment = comment.trim();
        mgrAt = Instant.now();
        mgrBy = byUser;
        finalRating = rating;
        calibrated = false;
        calibrationReason = null;
        status = PerfRules.SUBMITTED;
        touch();
    }

    public void setReviewer(UUID reviewerId) { this.reviewerId = reviewerId; touch(); }

    public void calibrate(int rating, String reason) { finalRating = rating; calibrated = true; calibrationReason = reason.trim(); touch(); }

    public void share(UUID by) { status = PerfRules.SHARED; sharedAt = Instant.now(); sharedBy = by; touch(); }

    public void acknowledge(UUID by, String comment) {
        status = PerfRules.ACKNOWLEDGED;
        ackAt = Instant.now();
        ackBy = by;
        employeeComment = comment == null || comment.isBlank() ? null : comment.trim();
        touch();
    }

    public void exclude(String reason) { status = PerfRules.EXCLUDED; excludedReason = reason.trim(); touch(); }

    private void touch() { updatedAt = Instant.now(); }
}
