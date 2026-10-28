package za.co.handyflow.platform.hr.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/** A saved report shared with a colleague: the saved view only, never figures. */
@Entity
@Table(name = "hr_saved_report_shares")
@Getter
@NoArgsConstructor
public class HrSavedReportShare {

    @Id UUID id;
    @Column(name = "report_id") UUID reportId;
    @Column(name = "tenant_id") UUID tenantId;
    @Column(name = "user_id")   UUID userId;
    @Column(name = "shared_by") UUID sharedBy;
    @Column(name = "shared_at") Instant sharedAt;

    public static HrSavedReportShare of(UUID tenantId, UUID reportId, UUID userId, UUID sharedBy) {
        HrSavedReportShare s = new HrSavedReportShare();
        s.id = UUID.randomUUID();
        s.tenantId = tenantId;
        s.reportId = reportId;
        s.userId = userId;
        s.sharedBy = sharedBy;
        s.sharedAt = Instant.now();
        return s;
    }
}
