package za.co.handyflow.platform.complianceservices.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** A rule that can only raise who must approve a quote. Proposed by one administrator, active when a different one activates it, retired with a reason. */
@Entity(name = "ComplianceApprovalRule")
@Table(name = "compliance_approval_rules")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ComplianceApprovalRule {
    @Id
    private UUID id = UUID.randomUUID();
    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;
    @Column(nullable = false)
    private String name;
    @Column(name = "min_fees", precision = 14, scale = 2)
    private BigDecimal minFees;
    @Column(name = "service_code")
    private String serviceCode;
    @Column(name = "risk_rating")
    private String riskRating;
    @Column(name = "client_id")
    private UUID clientId;
    @Column(name = "required_approver", nullable = false)
    private String requiredApprover;
    @Column(nullable = false, columnDefinition = "TEXT")
    private String reason;
    @Column(nullable = false)
    private String status = ApprovalRules.PROPOSED;
    @Column(name = "proposed_by", nullable = false)
    private UUID proposedBy;
    @Column(name = "proposed_at", nullable = false, updatable = false)
    private Instant proposedAt;
    @Column(name = "activated_by")
    private UUID activatedBy;
    @Column(name = "activated_at")
    private Instant activatedAt;
    @Column(name = "retired_by")
    private UUID retiredBy;
    @Column(name = "retired_at")
    private Instant retiredAt;
    @Column(name = "retire_note", columnDefinition = "TEXT")
    private String retireNote;
    @Version
    private Long version;

    public static ComplianceApprovalRule propose(TenantId tenantId, String name, BigDecimal minFees, String serviceCode, String riskRating, UUID clientId,
                                                 String requiredApprover, String reason, UUID by) {
        String code = serviceCode == null || serviceCode.isBlank() ? null : serviceCode.trim();
        String p = ApprovalRules.problem(name, minFees, code, riskRating, clientId, requiredApprover, reason);
        if (p != null) throw new IllegalArgumentException(p);
        ComplianceApprovalRule r = new ComplianceApprovalRule();
        r.tenantId = tenantId; r.name = name.trim(); r.minFees = minFees; r.serviceCode = code; r.riskRating = riskRating; r.clientId = clientId;
        r.requiredApprover = requiredApprover; r.reason = reason.trim(); r.proposedBy = by; r.proposedAt = Instant.now();
        return r;
    }

    public void activate(UUID by) {
        if (!ApprovalRules.PROPOSED.equals(status)) throw new IllegalStateException("Only a proposed rule can be activated");
        if (by == null || by.equals(proposedBy)) throw new IllegalStateException("A different administrator must activate a rule you proposed");
        this.status = ApprovalRules.ACTIVE; this.activatedBy = by; this.activatedAt = Instant.now();
    }

    public void retire(UUID by, String note) {
        if (ApprovalRules.RETIRED.equals(status)) throw new IllegalStateException("This rule is already retired");
        this.retireNote = ApprovalRules.note(note);
        this.status = ApprovalRules.RETIRED; this.retiredBy = by; this.retiredAt = Instant.now();
    }

    public ApprovalRules.Rule asRule() { return new ApprovalRules.Rule(name, minFees, serviceCode, riskRating, clientId, requiredApprover); }
}
