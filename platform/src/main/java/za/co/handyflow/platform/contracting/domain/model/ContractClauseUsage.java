package za.co.handyflow.platform.contracting.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/** Records that a clause version went into a contract, so a later withdrawal or expiry of that clause can be spotted. */
@Entity
@Table(name = "contract_clause_usage")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ContractClauseUsage {
    @Id private UUID id;
    @Column(name = "tenant_id") private UUID tenantId;
    @Column(name = "contract_id") private UUID contractId;
    @Column(name = "clause_id") private UUID clauseId;
    @Column(name = "clause_version_no") private int clauseVersionNo;
    @Column(name = "proposal_id") private UUID proposalId;
    @Column(name = "contract_version_no") private Integer contractVersionNo;
    @Column(name = "inserted_by") private UUID insertedBy;
    @Column(name = "inserted_at") private Instant insertedAt;

    public static ContractClauseUsage of(UUID tenantId, UUID contractId, ContractClause clause, UUID proposalId, int contractVersionNo, UUID by) {
        ContractClauseUsage u = new ContractClauseUsage();
        u.id = UUID.randomUUID();
        u.tenantId = tenantId;
        u.contractId = contractId;
        u.clauseId = clause.getId();
        u.clauseVersionNo = clause.getVersionNo();
        u.proposalId = proposalId;
        u.contractVersionNo = contractVersionNo;
        u.insertedBy = by;
        u.insertedAt = Instant.now();
        return u;
    }
}
