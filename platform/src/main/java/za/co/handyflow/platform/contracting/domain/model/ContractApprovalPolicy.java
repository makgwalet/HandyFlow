package za.co.handyflow.platform.contracting.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/** A tenant's override of the default approval rule for one action. */
@Entity
@Table(name = "contract_approval_policies")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ContractApprovalPolicy {
    @Id private UUID id;
    @Column(name = "tenant_id") private UUID tenantId;
    @Enumerated(EnumType.STRING) private ApprovalAction action;
    private boolean required;
    @Column(name = "allow_sole_user_exception") private boolean allowSoleUserException;
    @Column(name = "updated_by") private UUID updatedBy;
    @Column(name = "updated_at") private Instant updatedAt;

    public static ContractApprovalPolicy create(UUID tenantId, ApprovalAction action) {
        ContractApprovalPolicy p = new ContractApprovalPolicy();
        p.id = UUID.randomUUID();
        p.tenantId = tenantId;
        p.action = action;
        return p;
    }

    public void set(boolean required, boolean allowSoleUserException, UUID by) {
        this.required = required;
        this.allowSoleUserException = allowSoleUserException;
        this.updatedBy = by;
        this.updatedAt = Instant.now();
    }
}
