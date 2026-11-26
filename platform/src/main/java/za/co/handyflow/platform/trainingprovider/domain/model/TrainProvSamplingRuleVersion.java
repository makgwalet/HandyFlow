package za.co.handyflow.platform.trainingprovider.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** One version of a sampling rule's settings. Written once and never changed: a change is a new version. */
@Entity(name = "TrainProvSamplingRuleVersion")
@Table(name = "trainprov_sampling_rule_versions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TrainProvSamplingRuleVersion {
    @Id
    private UUID id = UUID.randomUUID();
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;
    @Column(name = "rule_id", nullable = false, updatable = false)
    private UUID ruleId;
    @Column(name = "version_no", nullable = false, updatable = false)
    private int versionNo;
    @Column(name = "effective_from", nullable = false, updatable = false)
    private LocalDate effectiveFrom;
    @Column(nullable = false, updatable = false)
    private String basis;
    @Column(updatable = false)
    private BigDecimal percentage;
    @Column(name = "min_count", updatable = false)
    private Integer minCount;
    @Column(name = "max_count", updatable = false)
    private Integer maxCount;
    @Column(name = "selection_method", nullable = false, updatable = false)
    private String selectionMethod;
    @Column(name = "risk_factors", updatable = false)
    private String riskFactors;
    @Column(name = "extra_trigger_queries", updatable = false)
    private Integer extraTriggerQueries;
    @Column(name = "extra_lookback", updatable = false)
    private Integer extraLookback;
    @Column(name = "extra_percentage", updatable = false)
    private BigDecimal extraPercentage;
    @Column(nullable = false, updatable = false)
    private String rationale;
    @Column(name = "created_by", nullable = false, updatable = false)
    private UUID createdBy;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public static TrainProvSamplingRuleVersion create(TenantId t, UUID ruleId, int no, LocalDate from, String basis, BigDecimal percentage, Integer min, Integer max,
                                                      String method, String riskFactors, Integer trigger, Integer lookback, BigDecimal extraPct, String rationale, UUID by) {
        TrainProvSamplingRuleVersion v = new TrainProvSamplingRuleVersion();
        v.tenantId = t.getValue();
        v.ruleId = ruleId;
        v.versionNo = no;
        v.effectiveFrom = from;
        v.basis = basis;
        v.percentage = percentage;
        v.minCount = min;
        v.maxCount = max;
        v.selectionMethod = method;
        v.riskFactors = riskFactors == null || riskFactors.isBlank() ? null : riskFactors.trim();
        v.extraTriggerQueries = trigger;
        v.extraLookback = lookback;
        v.extraPercentage = extraPct;
        v.rationale = rationale.trim();
        v.createdBy = by;
        v.createdAt = Instant.now();
        return v;
    }
}
