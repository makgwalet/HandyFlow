package za.co.handyflow.platform.complianceservices.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** One obligation as judged in an assessment. The status is always recomputed from applicability, evidence and control. */
@Entity
@Table(name = "compliance_assessment_items")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ComplianceAssessmentItem {

    @Id
    private UUID id = UUID.randomUUID();

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;

    @Column(name = "assessment_id", nullable = false)
    private UUID assessmentId;

    @Column(name = "obligation_id", nullable = false)
    private UUID obligationId;

    @Column(name = "version_id", nullable = false)
    private UUID versionId;

    @Column(name = "version_number", nullable = false)
    private int versionNumber;

    @Column(nullable = false)
    private String code;

    private String name;

    @Column(name = "risk_level")
    private String riskLevel;

    @Column(nullable = false)
    private String applicability;

    @Column(name = "evidence_status", nullable = false)
    private String evidenceStatus;

    @Column(nullable = false)
    private String control = "UNKNOWN";

    @Column(nullable = false)
    private String workflow = "NONE";

    @Column(nullable = false)
    private String status;

    @Column(columnDefinition = "TEXT")
    private String note;

    @Column(name = "assessed_by")
    private UUID assessedBy;

    @Column(name = "assessed_at")
    private Instant assessedAt;

    @Version
    private Long version;

    public static ComplianceAssessmentItem of(TenantId tenantId, UUID assessmentId, UUID obligationId, UUID versionId, int versionNumber,
                                              String code, String name, String riskLevel, String applicability, String evidenceStatus) {
        ComplianceAssessmentItem i = new ComplianceAssessmentItem();
        i.tenantId = tenantId;
        i.assessmentId = assessmentId;
        i.obligationId = obligationId;
        i.versionId = versionId;
        i.versionNumber = versionNumber;
        i.code = code;
        i.name = name;
        i.riskLevel = riskLevel;
        i.applicability = applicability;
        i.evidenceStatus = evidenceStatus;
        i.status = AssessmentRules.status(applicability, evidenceStatus, "UNKNOWN");
        return i;
    }

    /** A person's judgement. Evidence and applicability stay as they were checked. */
    public void judge(String controlRaw, String workflowRaw, String noteRaw, UUID by) {
        if (by == null) throw new IllegalArgumentException("A person is required");
        String c = AssessmentRules.control(controlRaw);
        String w = AssessmentRules.workflow(workflowRaw, noteRaw);
        String n = noteRaw == null || noteRaw.isBlank() ? null : ClientProfileRules.clean(noteRaw, 2000);
        if (("NO".equals(c) || "PARTIAL".equals(c)) && (n == null || n.length() < AssessmentRules.MIN_NOTE)) {
            throw new IllegalArgumentException("Say what is missing, in at least " + AssessmentRules.MIN_NOTE + " characters");
        }
        this.control = c;
        this.workflow = w;
        this.note = n;
        this.status = AssessmentRules.status(applicability, evidenceStatus, c);
        this.assessedBy = by;
        this.assessedAt = Instant.now();
    }

    /** Evidence was checked again. The person's judgement is kept. */
    public void recheck(String evidenceStatus) {
        this.evidenceStatus = evidenceStatus;
        this.status = AssessmentRules.status(applicability, evidenceStatus, control);
    }

    public String displayStatus() { return AssessmentRules.display(status, workflow); }
}
