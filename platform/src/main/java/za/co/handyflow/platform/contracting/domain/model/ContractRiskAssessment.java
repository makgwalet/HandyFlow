package za.co.handyflow.platform.contracting.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/** A stored risk check: which version of the text was checked, how many rules ran, and the findings (as JSON). Never changed afterwards. */
@Entity
@Table(name = "contract_risk_assessments")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ContractRiskAssessment {
    @Id private UUID id;
    @Column(name = "tenant_id") private UUID tenantId;
    @Column(name = "contract_id") private UUID contractId;
    @Column(name = "contract_version_no") private Integer contractVersionNo;
    @Column(name = "risk_level") private String riskLevel;
    @Column(name = "rules_evaluated") private int rulesEvaluated;
    @Column(name = "rules_skipped") private int rulesSkipped;
    @Column(name = "findings_json", columnDefinition = "TEXT") private String findingsJson;
    @Column(name = "assessed_by") private UUID assessedBy;
    @Column(name = "assessed_at") private Instant assessedAt;

    public static ContractRiskAssessment of(UUID tenantId, UUID contractId, Integer versionNo, String level, int evaluated, int skipped, String findingsJson, UUID by) {
        ContractRiskAssessment a = new ContractRiskAssessment();
        a.id = UUID.randomUUID();
        a.tenantId = tenantId;
        a.contractId = contractId;
        a.contractVersionNo = versionNo;
        a.riskLevel = level;
        a.rulesEvaluated = evaluated;
        a.rulesSkipped = skipped;
        a.findingsJson = findingsJson;
        a.assessedBy = by;
        a.assessedAt = Instant.now();
        return a;
    }
}
