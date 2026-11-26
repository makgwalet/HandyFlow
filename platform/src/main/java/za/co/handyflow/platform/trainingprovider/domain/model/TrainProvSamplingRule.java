package za.co.handyflow.platform.trainingprovider.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.util.UUID;

/** A moderation sampling rule: where it applies. The settings live in versions that never change. Retired with a reason, never deleted. */
@Entity(name = "TrainProvSamplingRule")
@Table(name = "trainprov_sampling_rules")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TrainProvSamplingRule {
    public static final String ACTIVE = "ACTIVE";
    public static final String RETIRED = "RETIRED";

    @Id
    private UUID id = UUID.randomUUID();
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;
    @Column(name = "rule_no", nullable = false, updatable = false)
    private String ruleNo;
    @Column(nullable = false)
    private String name;
    @Column(name = "scope_type", nullable = false, updatable = false)
    private String scopeType;
    @Column(name = "programme_id", updatable = false)
    private UUID programmeId;
    @Column(name = "cohort_id", updatable = false)
    private UUID cohortId;
    @Column(name = "assessment_type", updatable = false)
    private String assessmentType;
    @Column(nullable = false)
    private String status = ACTIVE;
    @Column(name = "retired_reason")
    private String retiredReason;
    @Column(name = "retired_by")
    private UUID retiredBy;
    @Column(name = "retired_at")
    private Instant retiredAt;
    @Column(name = "created_by", nullable = false, updatable = false)
    private UUID createdBy;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
    @Version
    private Long version;

    public static TrainProvSamplingRule create(TenantId t, String no, String name, String scopeType, UUID programmeId, UUID cohortId, String assessmentType, UUID by) {
        TrainProvSamplingRule r = new TrainProvSamplingRule();
        r.tenantId = t.getValue();
        r.ruleNo = no;
        r.name = name.trim();
        r.scopeType = scopeType;
        r.programmeId = programmeId;
        r.cohortId = cohortId;
        r.assessmentType = assessmentType;
        r.createdBy = by;
        r.createdAt = Instant.now();
        return r;
    }

    public boolean isActive() {
        return ACTIVE.equals(status);
    }

    public void rename(String newName) {
        this.name = newName.trim();
    }

    public void retire(String reason, UUID by) {
        if (!isActive()) {
            throw new IllegalStateException("This rule is already retired.");
        }
        if (reason == null || reason.trim().length() < 10) {
            throw new IllegalArgumentException("Give a reason of at least 10 characters.");
        }
        this.status = RETIRED;
        this.retiredReason = reason.trim();
        this.retiredBy = by;
        this.retiredAt = Instant.now();
    }
}
