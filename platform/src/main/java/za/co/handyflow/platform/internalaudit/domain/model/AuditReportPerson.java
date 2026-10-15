package za.co.handyflow.platform.internalaudit.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/** Someone named on a report: a REVIEWER (checks facts in the draft) or a RECIPIENT (may read the issued report; each reading is counted). */
@Entity(name = "AuditReportPerson")
@Table(name = "audit_report_people")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AuditReportPerson {
    @Id private UUID id = UUID.randomUUID();
    @Column(name = "tenant_id", nullable = false) private UUID tenantId;
    @Column(name = "report_id", nullable = false) private UUID reportId;
    @Column(name = "kind", nullable = false) private String kind;
    @Column(name = "user_id", nullable = false) private UUID userId;
    @Column(name = "user_name", nullable = false) private String userName;
    @Column(name = "added_by", nullable = false) private UUID addedBy;
    @Column(name = "added_at", nullable = false) private Instant addedAt = Instant.now();
    @Column(name = "first_viewed_at") private Instant firstViewedAt;
    @Column(name = "last_viewed_at") private Instant lastViewedAt;
    @Column(name = "view_count", nullable = false) private int viewCount;

    public static AuditReportPerson of(UUID tenantId, UUID reportId, String kind, UUID userId, String userName, UUID by) {
        if (!"REVIEWER".equals(kind) && !"RECIPIENT".equals(kind)) throw new IllegalArgumentException("Kind must be REVIEWER or RECIPIENT");
        AuditReportPerson p = new AuditReportPerson();
        p.tenantId = tenantId; p.reportId = reportId; p.kind = kind; p.userId = userId; p.userName = userName; p.addedBy = by;
        return p;
    }

    public void recordView() {
        Instant now = Instant.now();
        if (firstViewedAt == null) firstViewedAt = now;
        lastViewedAt = now; viewCount++;
    }
}
