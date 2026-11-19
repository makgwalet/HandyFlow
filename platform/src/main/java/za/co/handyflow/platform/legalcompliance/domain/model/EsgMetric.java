package za.co.handyflow.platform.legalcompliance.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.AggregateRoot;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;

/** An ESG measure the organisation tracks (for example Scope 2 emissions, lost-time injury rate, board independence). A target is required unless the metric is track-only. */
@Entity
@Table(name = "legalcompliance_esg_metrics")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EsgMetric extends AggregateRoot<EsgMetric> {

    public static final int MIN_REASON = 10;

    @Column(nullable = false, length = 20) private String reference;
    @Column(nullable = false, length = 255) private String name;
    @Column(columnDefinition = "TEXT") private String description;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 13) private EsgCategory category;
    @Column(nullable = false, length = 40) private String unit;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 10) private EsgFrequency frequency;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 16) private EsgDirection direction;
    @Column(name = "target_value", precision = 18, scale = 4) private BigDecimal targetValue;
    @Column(name = "owner_name", nullable = false, length = 255) private String ownerName;
    @Column(name = "framework_ref", length = 100) private String frameworkRef;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 8) private EsgMetricStatus status = EsgMetricStatus.ACTIVE;
    @Column(name = "retired_reason", columnDefinition = "TEXT") private String retiredReason;

    public static EsgMetric create(TenantId t, String reference, String name, String description, EsgCategory category, String unit, EsgFrequency frequency, EsgDirection direction,
                                   BigDecimal target, String owner, String frameworkRef) {
        EsgMetric m = new EsgMetric();
        m.initTenantId(t);
        m.reference = reference;
        m.apply(name, description, category, unit, frequency, direction, target, owner, frameworkRef);
        return m;
    }

    /** Editing the target does not change any reading already recorded: each reading kept the target that applied to it. */
    public void update(String name, String description, EsgCategory category, String unit, EsgFrequency frequency, EsgDirection direction, BigDecimal target, String owner, String frameworkRef) {
        if (status == EsgMetricStatus.RETIRED) throw new IllegalStateException("A retired metric cannot be edited");
        apply(name, description, category, unit, frequency, direction, target, owner, frameworkRef);
    }

    private void apply(String name, String description, EsgCategory category, String unit, EsgFrequency frequency, EsgDirection direction, BigDecimal target, String owner, String frameworkRef) {
        if (name == null || name.isBlank()) throw new IllegalArgumentException("A name is required");
        if (category == null) throw new IllegalArgumentException("A category is required");
        if (unit == null || unit.isBlank()) throw new IllegalArgumentException("A unit is required");
        if (frequency == null) throw new IllegalArgumentException("A reporting frequency is required");
        if (direction == null) throw new IllegalArgumentException("A direction is required");
        if (owner == null || owner.isBlank()) throw new IllegalArgumentException("An owner is required");
        if (direction == EsgDirection.TRACK_ONLY && target != null) throw new IllegalArgumentException("A track-only metric has no target");
        if (direction != EsgDirection.TRACK_ONLY && target == null) throw new IllegalArgumentException("A target is required, or choose track-only");
        this.name = name.trim();
        this.description = description == null || description.isBlank() ? null : description.trim();
        this.category = category;
        this.unit = unit.trim();
        this.frequency = frequency;
        this.direction = direction;
        this.targetValue = target;
        this.ownerName = owner.trim();
        this.frameworkRef = frameworkRef == null || frameworkRef.isBlank() ? null : frameworkRef.trim();
    }

    public void retire(String reason) {
        if (status == EsgMetricStatus.RETIRED) throw new IllegalStateException("This metric is already retired");
        if (reason == null || reason.trim().length() < MIN_REASON) throw new IllegalArgumentException("A reason of at least " + MIN_REASON + " characters is required");
        this.status = EsgMetricStatus.RETIRED;
        this.retiredReason = reason.trim();
    }
}
