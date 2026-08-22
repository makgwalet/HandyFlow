package za.co.handyflow.platform.approvals.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * ApprovalDelegation — "if delegatorUserId is the required approver on a
 * USER-type step, let delegateUserId act instead." Same "specific
 * person first, a resolvable fallback if they're unavailable" shape
 * already established twice elsewhere in this codebase
 * (hr.HrService.notifyApprover: manager → tenant admins;
 * crm.CustomerService.notifyNewLead: owner → tenant admins) — this
 * isn't a new pattern for the platform, just the first place it's
 * modeled as real persisted data instead of an inline fallback in a
 * notification method.
 * <p>
 * scopeModule nullable — null applies the delegation across every
 * module, a value scopes it to one (e.g. delegate AP approvals only
 * while scopeModule="ap", keep everything else with the original person).
 */
@Entity
@Table(name = "approval_delegations")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ApprovalDelegation {

    @Id
    private UUID id = UUID.randomUUID();

    @Column(name = "tenant_id", nullable = false) private UUID tenantId;
    @Column(name = "delegator_user_id", nullable = false) private UUID delegatorUserId;
    @Column(name = "delegate_user_id", nullable = false) private UUID delegateUserId;

    @Column(name = "start_date", nullable = false) private LocalDate startDate;
    @Column(name = "end_date") private LocalDate endDate; // nullable = indefinite until revoked

    @Column(name = "scope_module", length = 50) private String scopeModule; // nullable = every module

    private String reason;
    @Column(nullable = false) private boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;

    public static ApprovalDelegation create(UUID tenantId, UUID delegatorUserId, UUID delegateUserId,
                                            LocalDate startDate, LocalDate endDate,
                                            String scopeModule, String reason) {
        ApprovalDelegation d = new ApprovalDelegation();
        d.tenantId = tenantId;
        d.delegatorUserId = delegatorUserId;
        d.delegateUserId = delegateUserId;
        d.startDate = startDate;
        d.endDate = endDate;
        d.scopeModule = scopeModule;
        d.reason = reason;
        d.active = true;
        d.createdAt = Instant.now();
        return d;
    }

    public boolean coversToday(String module) {
        if (!active) return false;
        LocalDate today = LocalDate.now();
        if (today.isBefore(startDate)) return false;
        if (endDate != null && today.isAfter(endDate)) return false;
        return scopeModule == null || scopeModule.equals(module);
    }

    public void revoke() {
        this.active = false;
    }
}