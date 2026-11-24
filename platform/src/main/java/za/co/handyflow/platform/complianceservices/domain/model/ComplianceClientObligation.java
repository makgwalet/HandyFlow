package za.co.handyflow.platform.complianceservices.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** Whether one catalogue obligation applies to one client, and why. */
@Entity
@Table(name = "compliance_client_obligations")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ComplianceClientObligation {

    @Id
    private UUID id = UUID.randomUUID();

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;

    @Column(name = "client_id", nullable = false)
    private UUID clientId;

    @Column(name = "obligation_id", nullable = false)
    private UUID obligationId;

    @Column(name = "version_id", nullable = false)
    private UUID versionId;

    @Column(name = "version_number", nullable = false)
    private int versionNumber;

    @Column(nullable = false)
    private String state;

    @Column(name = "suggested_state", nullable = false)
    private String suggestedState;

    @Column(name = "suggestion_reasons", columnDefinition = "TEXT")
    private String suggestionReasons;

    @Column(name = "based_on_unconfirmed", nullable = false)
    private boolean basedOnUnconfirmed;

    @Column(name = "decided_by")
    private UUID decidedBy;

    @Column(name = "decided_at")
    private Instant decidedAt;

    @Column(name = "decided_version_number")
    private Integer decidedVersionNumber;

    @Column(name = "decision_reason", columnDefinition = "TEXT")
    private String decisionReason;

    @Column(name = "evidence_ref")
    private String evidenceRef;

    @Column(name = "effective_from")
    private LocalDate effectiveFrom;

    @Column(name = "review_due")
    private LocalDate reviewDue;

    @Column(name = "approval_status", nullable = false)
    private String approvalStatus = "NOT_REQUIRED";

    @Column(name = "approved_by")
    private UUID approvedBy;

    @Column(name = "approved_at")
    private Instant approvedAt;

    @Column(name = "approval_exception_reason", columnDefinition = "TEXT")
    private String approvalExceptionReason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private Long version;

    public static ComplianceClientObligation suggested(TenantId tenantId, UUID clientId, UUID obligationId, UUID versionId, int versionNumber,
                                                       ApplicabilityRules.Evaluation e) {
        ComplianceClientObligation a = new ComplianceClientObligation();
        a.tenantId = tenantId;
        a.clientId = clientId;
        a.obligationId = obligationId;
        a.versionId = versionId;
        a.versionNumber = versionNumber;
        a.state = e.state();
        a.suggestedState = e.state();
        a.suggestionReasons = e.reasons();
        a.basedOnUnconfirmed = e.basedOnUnconfirmed();
        a.createdAt = Instant.now();
        a.updatedAt = Instant.now();
        return a;
    }

    public boolean isDecidedByPerson() { return decidedBy != null; }

    /**
     * Takes in a fresh suggestion. The state follows it only while no person has decided; a person's decision stays,
     * and shows against the new suggestion. Returns true if the visible state changed.
     */
    public boolean resuggest(UUID versionId, int versionNumber, ApplicabilityRules.Evaluation e) {
        String before = state;
        this.versionId = versionId;
        this.versionNumber = versionNumber;
        this.suggestedState = e.state();
        this.suggestionReasons = e.reasons();
        this.basedOnUnconfirmed = e.basedOnUnconfirmed();
        if (!isDecidedByPerson()) this.state = e.state();
        this.updatedAt = Instant.now();
        return !before.equals(state);
    }

    public void decide(String newState, String reason, String evidenceRef, LocalDate effectiveFrom, LocalDate reviewDue, UUID by, LocalDate today) {
        if (by == null) throw new IllegalArgumentException("A person is required");
        String s = newState == null ? "" : newState.trim().toUpperCase();
        ApplicabilityRules.checkDecision(s, suggestedState, reason, reviewDue, today);
        this.state = s;
        this.decisionReason = ClientProfileRules.clean(reason, 2000);
        this.evidenceRef = ClientProfileRules.clean(evidenceRef, 300);
        this.effectiveFrom = effectiveFrom;
        this.reviewDue = ApplicabilityRules.NOT_APPLICABLE.equals(s) || reviewDue != null ? reviewDue : null;
        this.decidedBy = by;
        this.decidedAt = Instant.now();
        this.decidedVersionNumber = versionNumber;
        this.approvalStatus = ApplicabilityRules.needsApproval(s, suggestedState) ? "AWAITING" : "NOT_REQUIRED";
        this.approvedBy = null;
        this.approvedAt = null;
        this.approvalExceptionReason = null;
        this.updatedAt = Instant.now();
    }

    /** A second person agrees to the removal. The person who decided needs a recorded reason to do it themselves. */
    public void approve(UUID approver, String exceptionReason) {
        if (!"AWAITING".equals(approvalStatus)) throw new IllegalStateException("Nothing is waiting for approval");
        if (approver == null) throw new IllegalArgumentException("An approver is required");
        String reason = null;
        if (approver.equals(decidedBy)) {
            String r = exceptionReason == null ? "" : exceptionReason.trim();
            if (r.length() < ApplicabilityRules.MIN_REASON) {
                throw new IllegalArgumentException("You made this decision, so someone else should approve it. If no one else can, give a reason of at least "
                        + ApplicabilityRules.MIN_REASON + " characters");
            }
            reason = r.length() > 1000 ? r.substring(0, 1000) : r;
        }
        this.approvalStatus = "APPROVED";
        this.approvedBy = approver;
        this.approvedAt = Instant.now();
        this.approvalExceptionReason = reason;
        this.updatedAt = Instant.now();
    }

    public String effectiveState() { return ApplicabilityRules.effectiveState(state, approvalStatus); }
}
