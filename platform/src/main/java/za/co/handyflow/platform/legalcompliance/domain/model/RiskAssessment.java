package za.co.handyflow.platform.legalcompliance.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.AggregateRoot;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.util.UUID;

import za.co.handyflow.platform.legalcompliance.application.internal.RiskScoringRules;

/**
 * One scored assessment of a risk. APPEND-ONLY: a reassessment supersedes this row, it never edits it. The scores, ratings and the
 * appetite/tolerance in force are stored so the result can be reproduced after settings change.
 * Residual is what remains after existing controls; it can never exceed inherent. It is the assessor's judgement and is not yet
 * verified against control test results.
 */
@Entity(name = "LcRiskAssessment")
@Table(name = "legalcompliance_risk_assessments")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RiskAssessment extends AggregateRoot<RiskAssessment> {

    @Column(name = "risk_id", nullable = false) private UUID riskId;
    @Column(name = "inherent_likelihood", nullable = false) private int inherentLikelihood;
    @Column(name = "inherent_impact", nullable = false) private int inherentImpact;
    @Column(name = "residual_likelihood", nullable = false) private int residualLikelihood;
    @Column(name = "residual_impact", nullable = false) private int residualImpact;
    @Column(name = "inherent_score", nullable = false) private int inherentScore;
    @Column(name = "residual_score", nullable = false) private int residualScore;
    @Enumerated(EnumType.STRING) @Column(name = "inherent_rating", nullable = false, length = 10) private RiskRating inherentRating;
    @Enumerated(EnumType.STRING) @Column(name = "residual_rating", nullable = false, length = 10) private RiskRating residualRating;
    @Column(name = "appetite_max", nullable = false) private int appetiteMax;
    @Column(name = "tolerance_max", nullable = false) private int toleranceMax;
    @Column(nullable = false, columnDefinition = "TEXT") private String rationale;
    @Column(name = "assessed_by") private UUID assessedBy;
    @Column(name = "assessed_by_name", length = 255) private String assessedByName;
    @Column(name = "assessed_at", nullable = false) private Instant assessedAt;
    @Column(name = "is_current", nullable = false) private boolean inForce = true;
    @Column(name = "superseded_at") private Instant supersededAt;

    public static RiskAssessment create(TenantId tenantId, UUID riskId, int inherentLikelihood, int inherentImpact,
                                        int residualLikelihood, int residualImpact, int appetiteMax, int toleranceMax,
                                        String rationale, UUID by, String byName) {
        if (rationale == null || rationale.trim().length() < 15)
            throw new IllegalArgumentException("A rationale of at least 15 characters is required");
        int inherent = RiskScoringRules.score(inherentLikelihood, inherentImpact);
        int residual = RiskScoringRules.score(residualLikelihood, residualImpact);
        if (residual > inherent) throw new IllegalArgumentException("Residual risk cannot be higher than inherent risk");
        RiskAssessment a = new RiskAssessment();
        a.initTenantId(tenantId);
        a.riskId = riskId;
        a.inherentLikelihood = inherentLikelihood;
        a.inherentImpact = inherentImpact;
        a.residualLikelihood = residualLikelihood;
        a.residualImpact = residualImpact;
        a.inherentScore = inherent;
        a.residualScore = residual;
        a.inherentRating = RiskScoringRules.rating(inherent);
        a.residualRating = RiskScoringRules.rating(residual);
        a.appetiteMax = appetiteMax;
        a.toleranceMax = toleranceMax;
        a.rationale = rationale.trim();
        a.assessedBy = by;
        a.assessedByName = byName;
        a.assessedAt = Instant.now();
        return a;
    }

    public void supersede() {
        if (!inForce) return;
        this.inForce = false;
        this.supersededAt = Instant.now();
    }

    public za.co.handyflow.platform.legalcompliance.domain.model.RiskPosition residualPosition() {
        return RiskScoringRules.position(residualScore, appetiteMax, toleranceMax);
    }
}
