package za.co.handyflow.platform.legalcompliance.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.AggregateRoot;
import za.co.handyflow.platform.shared.TenantId;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Whether FICA applies to this tenant. FICA obligations fall on "accountable institutions" and similar listed persons, not on every business, so the
 * platform does nothing FICA-related until the tenant says it is one (and why). One row per tenant. The RMCP itself is a policy (see Policy); this row only
 * points at it and records when it was last approved and when it is next due for review.
 */
@Entity
@Table(name = "legalcompliance_fica_profiles")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FicaProfile extends AggregateRoot<FicaProfile> {

    public static final int MIN_TEXT = 15;

    @Column(name = "accountable_institution", nullable = false) private boolean accountableInstitution;
    @Column(nullable = false, columnDefinition = "TEXT") private String basis;
    @Column(name = "compliance_officer_name", length = 255) private String complianceOfficerName;
    @Column(name = "rmcp_policy_id") private UUID rmcpPolicyId;
    @Column(name = "rmcp_approved_on") private LocalDate rmcpApprovedOn;
    @Column(name = "rmcp_next_review") private LocalDate rmcpNextReview;
    @Column(name = "updated_by_name", length = 255) private String updatedByName;

    public static FicaProfile create(TenantId tenantId) {
        FicaProfile p = new FicaProfile();
        p.initTenantId(tenantId);
        p.basis = "";
        return p;
    }

    public void update(boolean accountable, String basis, String officer, UUID rmcpPolicyId, LocalDate approvedOn, LocalDate nextReview, String by) {
        if (basis == null || basis.trim().length() < MIN_TEXT) throw new IllegalArgumentException("Say why FICA does or does not apply to you (at least " + MIN_TEXT + " characters)");
        if (accountable && (officer == null || officer.isBlank())) throw new IllegalArgumentException("An accountable institution needs a named compliance officer");
        if (approvedOn != null && nextReview != null && !nextReview.isAfter(approvedOn)) throw new IllegalArgumentException("The next RMCP review must be after it was approved");
        if (!accountable && (rmcpPolicyId != null || approvedOn != null)) throw new IllegalArgumentException("RMCP details only apply to an accountable institution");
        this.accountableInstitution = accountable;
        this.basis = basis.trim();
        this.complianceOfficerName = officer == null || officer.isBlank() ? null : officer.trim();
        this.rmcpPolicyId = rmcpPolicyId;
        this.rmcpApprovedOn = approvedOn;
        this.rmcpNextReview = nextReview;
        this.updatedByName = by;
    }
}
