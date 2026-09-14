package za.co.handyflow.platform.internalaudit.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * Hybrid risk scoring, per the product owner's own explicit design:
 * "System calculates a preliminary risk score → Auditor reviews it →
 * Auditor can override it → Auditor records justification." Two risk
 * fields are stored, deliberately never collapsed into one —
 * systemCalculatedRisk (computed, see calculateLevel() below) and
 * finalAuditRisk (what the Annual Plan and everything downstream
 * actually reads). Overriding requires a reason; who approved the
 * override is captured separately from who performed the assessment.
 * <p>
 * V1 formula, matching the product owner's own "don't over-engineer
 * day one" guidance: five 1–5 inputs, summed with equal weight (range
 * 5–25), mapped to LOW/MEDIUM/HIGH/CRITICAL via even quartile-style
 * bands. Deliberately simple — the real value in V1 is the override/
 * justification audit trail, not formula sophistication; weights can
 * be revisited without a schema change since the raw component scores
 * are stored individually, not just their sum.
 */
@Entity
@Table(name = "audit_risk_assessments")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RiskAssessment {

    @Id
    private UUID id = UUID.randomUUID();

    @Column(name = "tenant_id", nullable = false) private UUID tenantId;
    @Column(name = "universe_entry_id", nullable = false) private UUID universeEntryId;

    @Column(name = "inherent_risk_score", nullable = false) private int inherentRiskScore;
    @Column(name = "control_risk_score", nullable = false) private int controlRiskScore;
    @Column(name = "historical_findings_score", nullable = false) private int historicalFindingsScore;
    @Column(name = "time_since_last_audit_score", nullable = false) private int timeSinceLastAuditScore;
    @Column(name = "business_regulatory_impact_score", nullable = false) private int businessRegulatoryImpactScore;

    @Column(name = "system_calculated_risk", nullable = false) private String systemCalculatedRisk;
    @Column(name = "final_audit_risk", nullable = false) private String finalAuditRisk;
    @Column(name = "override_reason") private String overrideReason;
    @Column(name = "override_approved_by") private UUID overrideApprovedBy;

    @Column(name = "assessed_by") private UUID assessedBy;
    @Column(name = "assessed_at", nullable = false, updatable = false) private Instant assessedAt;

    public static RiskAssessment create(UUID tenantId, UUID universeEntryId,
                                        int inherentRiskScore, int controlRiskScore,
                                        int historicalFindingsScore, int timeSinceLastAuditScore,
                                        int businessRegulatoryImpactScore, UUID assessedBy) {
        RiskAssessment r = new RiskAssessment();
        r.tenantId = tenantId;
        r.universeEntryId = universeEntryId;
        r.inherentRiskScore = inherentRiskScore;
        r.controlRiskScore = controlRiskScore;
        r.historicalFindingsScore = historicalFindingsScore;
        r.timeSinceLastAuditScore = timeSinceLastAuditScore;
        r.businessRegulatoryImpactScore = businessRegulatoryImpactScore;
        r.systemCalculatedRisk = calculateLevel(
                inherentRiskScore, controlRiskScore, historicalFindingsScore,
                timeSinceLastAuditScore, businessRegulatoryImpactScore);
        r.finalAuditRisk = r.systemCalculatedRisk; // defaults to the calculated value until overridden
        r.assessedBy = assessedBy;
        r.assessedAt = Instant.now();
        return r;
    }

    /**
     * Sum of the five 1–5 inputs (range 5–25) mapped to a level.
     * Equal-weighted V1, per the product owner's own guidance — kept as
     * its own named method (not inlined) so retuning the formula later
     * is a one-method change, not a schema migration.
     */
    private static String calculateLevel(int inherent, int control, int historical,
                                         int timeSinceAudit, int businessImpact) {
        int sum = inherent + control + historical + timeSinceAudit + businessImpact;
        if (sum <= 10) return "LOW";
        if (sum <= 15) return "MEDIUM";
        if (sum <= 20) return "HIGH";
        return "CRITICAL";
    }

    /**
     * The auditor's override. Reason is required whenever the override
     * genuinely changes the risk level from what was calculated —
     * matches the product owner's own "Payroll is actually critical
     * because..." example precisely: the calculated number is an input
     * to judgment, never a silent substitute for it.
     */
    public void override(String finalAuditRisk, String reason, UUID approvedBy) {
        if (!finalAuditRisk.equals(this.systemCalculatedRisk)) {
            if (reason == null || reason.isBlank()) {
                throw new IllegalArgumentException(
                        "A reason is required when the final audit risk differs from the system-calculated risk");
            }
        }
        this.finalAuditRisk = finalAuditRisk;
        this.overrideReason = reason;
        this.overrideApprovedBy = approvedBy;
    }
}
