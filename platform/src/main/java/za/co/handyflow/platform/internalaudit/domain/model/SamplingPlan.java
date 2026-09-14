package za.co.handyflow.platform.internalaudit.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * The full model the product owner specified verbatim: "I'd change the
 * concept to Sampling Plan. Because sample size is only one part of
 * sampling." Every field below is captured even in V1 — sampleSize
 * alone would be a much weaker workpaper. samplingMethod is fixed to
 * AUDITOR_JUDGMENT for V1 (statistical methods — attribute, variable,
 * monetary-unit sampling — are explicitly deferred per the product
 * owner's own "I would not build all of this into V1" guidance);
 * riskLevel and confidenceLevel are captured for context/future use
 * but not computed by this entity.
 */
@Entity
@Table(name = "audit_sampling_plans")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SamplingPlan {

    @Id
    private UUID id = UUID.randomUUID();

    @Column(name = "tenant_id", nullable = false) private UUID tenantId;
    @Column(name = "engagement_id", nullable = false) private UUID engagementId;

    @Column(name = "population", nullable = false) private int population;
    @Column(name = "population_value") private BigDecimal populationValue;
    @Column(name = "sampling_objective") private String samplingObjective;
    @Column(name = "sampling_method", nullable = false) private String samplingMethod = "AUDITOR_JUDGMENT";
    @Column(name = "risk_level") private String riskLevel;
    @Column(name = "confidence_level") private BigDecimal confidenceLevel;
    @Column(name = "expected_error_rate") private BigDecimal expectedErrorRate;
    @Column(name = "tolerable_error_rate") private BigDecimal tolerableErrorRate;
    @Column(name = "sample_size", nullable = false) private int sampleSize;
    @Column(name = "selection_method", nullable = false) private String selectionMethod = "RANDOM";
    @Column(name = "sample_period_from") private LocalDate samplePeriodFrom;
    @Column(name = "sample_period_to") private LocalDate samplePeriodTo;
    @Column(name = "exclusions") private String exclusions;
    @Column(name = "rationale") private String rationale;
    @Column(name = "prepared_by") private UUID preparedBy;
    @Column(name = "reviewed_by") private UUID reviewedBy;
    @Column(name = "status", nullable = false) private String status = "DRAFT"; // DRAFT | FINALIZED
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;

    public static SamplingPlan create(UUID tenantId, UUID engagementId, int population, BigDecimal populationValue,
                                      String samplingObjective, String riskLevel, BigDecimal expectedErrorRate,
                                      BigDecimal tolerableErrorRate, int sampleSize, String selectionMethod,
                                      LocalDate samplePeriodFrom, LocalDate samplePeriodTo, String exclusions,
                                      String rationale, UUID preparedBy) {
        SamplingPlan p = new SamplingPlan();
        p.tenantId = tenantId;
        p.engagementId = engagementId;
        p.population = population;
        p.populationValue = populationValue;
        p.samplingObjective = samplingObjective;
        p.riskLevel = riskLevel;
        p.expectedErrorRate = expectedErrorRate;
        p.tolerableErrorRate = tolerableErrorRate;
        p.sampleSize = sampleSize;
        p.selectionMethod = selectionMethod;
        p.samplePeriodFrom = samplePeriodFrom;
        p.samplePeriodTo = samplePeriodTo;
        p.exclusions = exclusions;
        p.rationale = rationale;
        p.preparedBy = preparedBy;
        p.createdAt = Instant.now();
        return p;
    }

    public void review(UUID reviewedBy) {
        this.reviewedBy = reviewedBy;
    }

    public void finalizePlan() {
        this.status = "FINALIZED";
    }
}
