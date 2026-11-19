package za.co.handyflow.platform.legalcompliance.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.AggregateRoot;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * A measured value for a period. Never edited: a correction is a new reading that supersedes this one (which is kept, with the reason). The metric's target and direction at the
 * time are copied onto the reading, so whether it met its target does not change if the metric is edited later.
 */
@Entity
@Table(name = "legalcompliance_esg_readings")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EsgReading extends AggregateRoot<EsgReading> {

    public static final int MIN_REASON = 10, MAX_PERIOD_DAYS = 366;

    @Column(name = "metric_id", nullable = false) private UUID metricId;
    @Column(name = "period_start", nullable = false) private LocalDate periodStart;
    @Column(name = "period_end", nullable = false) private LocalDate periodEnd;
    @Column(name = "reading_value", nullable = false, precision = 18, scale = 4) private BigDecimal value;
    @Column(columnDefinition = "TEXT") private String notes;
    @Column(name = "target_snapshot", precision = 18, scale = 4) private BigDecimal targetSnapshot;
    @Enumerated(EnumType.STRING) @Column(name = "direction_snapshot", nullable = false, length = 16) private EsgDirection directionSnapshot;
    @Column(name = "recorded_by_user_id", nullable = false) private UUID recordedByUserId;
    @Column(name = "recorded_by_name", nullable = false, length = 255) private String recordedByName;
    @Column(name = "corrects_id") private UUID correctsId;
    @Column(name = "superseded_at") private Instant supersededAt;
    @Column(name = "supersede_reason", columnDefinition = "TEXT") private String supersedeReason;

    public static EsgReading record(TenantId t, EsgMetric metric, LocalDate start, LocalDate end, BigDecimal value, String notes, UUID correctsId, UUID uid, String uname, LocalDate today) {
        if (metric.getStatus() == EsgMetricStatus.RETIRED) throw new IllegalStateException("Readings cannot be recorded for a retired metric");
        if (start == null || end == null) throw new IllegalArgumentException("The period start and end are required");
        if (end.isBefore(start)) throw new IllegalArgumentException("The period cannot end before it starts");
        if (end.isAfter(today)) throw new IllegalArgumentException("A reading cannot be for a period that has not ended");
        if (start.plusDays(MAX_PERIOD_DAYS).isBefore(end)) throw new IllegalArgumentException("A reporting period cannot be longer than a year");
        if (value == null) throw new IllegalArgumentException("A value is required");
        if (uid == null) throw new IllegalArgumentException("The person recording is required");
        EsgReading r = new EsgReading();
        r.initTenantId(t);
        r.metricId = metric.getId();
        r.periodStart = start;
        r.periodEnd = end;
        r.value = value;
        r.notes = notes == null || notes.isBlank() ? null : notes.trim();
        r.targetSnapshot = metric.getTargetValue();
        r.directionSnapshot = metric.getDirection();
        r.recordedByUserId = uid;
        r.recordedByName = uname == null || uname.isBlank() ? "Unknown" : uname;
        r.correctsId = correctsId;
        return r;
    }

    public void supersede(String reason, Instant now) {
        if (supersededAt != null) throw new IllegalStateException("This reading has already been corrected");
        if (reason == null || reason.trim().length() < MIN_REASON) throw new IllegalArgumentException("A reason of at least " + MIN_REASON + " characters is required");
        this.supersededAt = now;
        this.supersedeReason = reason.trim();
    }

    public boolean current() { return supersededAt == null; }
}
