package za.co.handyflow.platform.contracting.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/** What the business decided about a signed contract nearing its end date. Kept as history; a later decision does not erase an earlier one. */
@Entity
@Table(name = "contract_renewal_decisions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ContractRenewalDecision {

    public static final Set<String> DECISIONS = Set.of("RENEW", "RENEGOTIATE", "DO_NOT_RENEW");

    @Id private UUID id;
    @Column(name = "tenant_id") private UUID tenantId;
    @Column(name = "contract_id") private UUID contractId;
    private String decision;
    @Column(columnDefinition = "TEXT") private String note;
    @Column(name = "decided_by") private UUID decidedBy;
    @Column(name = "decided_at") private Instant decidedAt;
    @Column(name = "successor_contract_id") private UUID successorContractId;

    public static ContractRenewalDecision record(UUID tenantId, UUID contractId, String decision, String note, UUID by, UUID successorContractId) {
        String d = decision == null ? "" : decision.trim().toUpperCase();
        if (!DECISIONS.contains(d)) throw new IllegalArgumentException("Decision must be RENEW, RENEGOTIATE or DO_NOT_RENEW");
        ContractRenewalDecision r = new ContractRenewalDecision();
        r.id = UUID.randomUUID();
        r.tenantId = tenantId;
        r.contractId = contractId;
        r.decision = d;
        r.note = note;
        r.decidedBy = by;
        r.decidedAt = Instant.now();
        r.successorContractId = successorContractId;
        return r;
    }
}
