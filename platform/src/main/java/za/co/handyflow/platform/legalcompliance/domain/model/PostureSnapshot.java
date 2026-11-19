package za.co.handyflow.platform.legalcompliance.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import za.co.handyflow.platform.shared.AggregateRoot;
import za.co.handyflow.platform.shared.TenantId;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A dated record of GRC measures. metrics holds raw counts only (never a composite score), so any percentage shown for that day can be recomputed from
 * what was recorded. One snapshot per tenant per day: the service refuses a second one for the same day.
 */
@Entity
@Table(name = "legalcompliance_posture_snapshots")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PostureSnapshot extends AggregateRoot<PostureSnapshot> {

    @Column(name = "snapshot_date", nullable = false) private LocalDate snapshotDate;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 10) private SnapshotSource source;
    @Column(name = "taken_by_name", length = 255) private String takenByName;
    @JdbcTypeCode(SqlTypes.JSON) @Column(nullable = false, columnDefinition = "jsonb") private Map<String, Long> metrics;

    public static PostureSnapshot take(TenantId tenantId, LocalDate date, SnapshotSource source, String by, Map<String, Long> metrics) {
        if (date == null) throw new IllegalArgumentException("A snapshot date is required");
        if (metrics == null || metrics.isEmpty()) throw new IllegalArgumentException("A snapshot needs metrics");
        PostureSnapshot s = new PostureSnapshot();
        s.initTenantId(tenantId);
        s.snapshotDate = date;
        s.source = source;
        s.takenByName = by;
        s.metrics = new LinkedHashMap<>(metrics);
        return s;
    }
}
