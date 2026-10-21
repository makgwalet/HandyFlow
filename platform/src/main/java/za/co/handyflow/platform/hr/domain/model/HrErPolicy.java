package za.co.handyflow.platform.hr.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/** The company's employee relations settings. One row per company; the defaults apply until it has been saved. */
@Entity
@Table(name = "hr_er_policy")
@Getter
@NoArgsConstructor
public class HrErPolicy {

    @Id @Column(name = "tenant_id") UUID tenantId;
    @Column(name = "notice_hours")            int noticeHours;
    @Column(name = "verbal_months")           int verbalMonths;
    @Column(name = "written_months")          int writtenMonths;
    @Column(name = "final_months")            int finalMonths;
    @Column(name = "appeal_days")             int appealDays;
    @Column(name = "grievance_response_days") int grievanceResponseDays;
    @Column(name = "min_review_days")         int minReviewDays;
    @Column(name = "updated_by") UUID updatedBy;
    @Column(name = "updated_at") Instant updatedAt;

    public static HrErPolicy of(UUID tenantId, ErPolicy p, UUID by) {
        HrErPolicy e = new HrErPolicy();
        e.tenantId = tenantId;
        e.apply(p, by);
        return e;
    }

    public void apply(ErPolicy p, UUID by) {
        noticeHours = p.noticeHours();
        verbalMonths = p.verbalMonths();
        writtenMonths = p.writtenMonths();
        finalMonths = p.finalMonths();
        appealDays = p.appealDays();
        grievanceResponseDays = p.grievanceResponseDays();
        minReviewDays = p.minReviewDays();
        updatedBy = by;
        updatedAt = Instant.now();
    }

    public ErPolicy toPolicy() {
        return new ErPolicy(noticeHours, verbalMonths, writtenMonths, finalMonths, appealDays, grievanceResponseDays, minReviewDays);
    }
}
