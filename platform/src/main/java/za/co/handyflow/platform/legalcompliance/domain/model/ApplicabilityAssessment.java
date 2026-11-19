package za.co.handyflow.platform.legalcompliance.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import za.co.handyflow.platform.shared.AggregateRoot;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * A justified decision that one requirement does, or does not, apply to this organisation.
 * Append-only in spirit: a change of mind is a NEW assessment that supersedes this one once approved.
 * The suggestion and its reasons at the time are frozen on the row so the decision stays explainable.
 */
@Entity
@Table(name = "legalcompliance_applicability_assessments")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ApplicabilityAssessment extends AggregateRoot<ApplicabilityAssessment> {

    @Column(name = "requirement_id", nullable = false) private UUID requirementId;
    @Column(name = "version_id", nullable = false) private UUID versionId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ApplicabilityDecision decision;

    @Column(nullable = false, columnDefinition = "TEXT") private String justification;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AssessmentStatus status;

    @Enumerated(EnumType.STRING)
    @Column(length = 30)
    private ApplicabilitySuggestion suggestion;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "suggestion_reasons", nullable = false, columnDefinition = "jsonb")
    private List<String> suggestionReasons = new ArrayList<>();

    @Column(name = "assessed_by") private UUID assessedBy;
    @Column(name = "assessed_by_name", length = 255) private String assessedByName;
    @Column(name = "assessed_at", nullable = false) private Instant assessedAt;
    @Column(name = "decided_by") private UUID decidedBy;
    @Column(name = "decided_by_name", length = 255) private String decidedByName;
    @Column(name = "decided_at") private Instant decidedAt;
    @Column(name = "decision_note", columnDefinition = "TEXT") private String decisionNote;
    @Column(name = "superseded_at") private Instant supersededAt;

    /** Set when this decision was carried across to an unchanged requirement in a newer version (regulatory change). */
    @Column(name = "carried_forward_from") private UUID carriedForwardFrom;

    public static ApplicabilityAssessment create(TenantId tenantId, UUID requirementId, UUID versionId,
                                                 ApplicabilityDecision decision, String justification,
                                                 ApplicabilitySuggestion suggestion, List<String> reasons,
                                                 UUID by, String byName) {
        ApplicabilityAssessment a = new ApplicabilityAssessment();
        a.initTenantId(tenantId);
        a.requirementId = requirementId;
        a.versionId = versionId;
        a.decision = decision;
        a.justification = justification;
        a.suggestion = suggestion;
        a.suggestionReasons = reasons == null ? new ArrayList<>() : new ArrayList<>(reasons);
        a.assessedBy = by;
        a.assessedByName = byName;
        a.assessedAt = Instant.now();
        if (decision == ApplicabilityDecision.APPLICABLE) {
            // Adding a duty needs no second pair of eyes; hiding one does.
            a.status = AssessmentStatus.APPROVED;
            a.decidedBy = by;
            a.decidedByName = byName;
            a.decidedAt = a.assessedAt;
        } else {
            a.status = AssessmentStatus.PENDING_APPROVAL;
        }
        return a;
    }

    /**
     * A decision carried to the same, unchanged requirement in a newer regulatory version. Always APPROVED with the
     * original decision (an exclusion that was already approved stays approved — the text it was approved against is identical).
     */
    public static ApplicabilityAssessment carryForward(TenantId tenantId, ApplicabilityAssessment from, UUID newRequirementId,
                                                       UUID newVersionId, String fromVersionLabel, UUID by, String byName) {
        ApplicabilityAssessment a = new ApplicabilityAssessment();
        a.initTenantId(tenantId);
        a.requirementId = newRequirementId;
        a.versionId = newVersionId;
        a.decision = from.decision;
        a.justification = from.justification;
        a.suggestion = from.suggestion;
        a.suggestionReasons = new ArrayList<>(from.suggestionReasons);
        a.assessedBy = from.assessedBy;
        a.assessedByName = from.assessedByName;
        a.assessedAt = from.assessedAt;
        a.status = AssessmentStatus.APPROVED;
        a.decidedBy = from.decidedBy;
        a.decidedByName = from.decidedByName;
        a.decidedAt = from.decidedAt;
        a.decisionNote = "Carried forward unchanged from " + fromVersionLabel + " by " + (byName == null ? "the publisher" : byName);
        a.carriedForwardFrom = from.getId();
        return a;
    }

    public void approve(UUID by, String byName, String note) {
        requirePending("approved");
        status = AssessmentStatus.APPROVED;
        decidedBy = by;
        decidedByName = byName;
        decidedAt = Instant.now();
        decisionNote = note;
    }

    public void reject(UUID by, String byName, String reason) {
        requirePending("rejected");
        status = AssessmentStatus.REJECTED;
        decidedBy = by;
        decidedByName = byName;
        decidedAt = Instant.now();
        decisionNote = reason;
    }

    public void supersede() {
        if (status != AssessmentStatus.APPROVED)
            throw new IllegalStateException("Only an APPROVED assessment can be superseded — current: " + status);
        status = AssessmentStatus.SUPERSEDED;
        supersededAt = Instant.now();
    }

    public boolean isInForce() { return status == AssessmentStatus.APPROVED; }

    private void requirePending(String action) {
        if (status != AssessmentStatus.PENDING_APPROVAL)
            throw new IllegalStateException("Only a PENDING_APPROVAL assessment can be " + action + " — current: " + status);
    }
}
