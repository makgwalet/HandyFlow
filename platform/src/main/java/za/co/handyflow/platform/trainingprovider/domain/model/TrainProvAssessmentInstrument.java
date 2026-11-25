package za.co.handyflow.platform.trainingprovider.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;
import za.co.handyflow.platform.trainingprovider.domain.rules.AssessmentRules;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** An assessment instrument for a programme version. Approved instruments are never edited: they are retired and replaced. No pass mark is held. */
@Entity(name = "TrainProvAssessmentInstrument")
@Table(name = "trainprov_assessment_instruments")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TrainProvAssessmentInstrument {
    @Id
    private UUID id = UUID.randomUUID();
    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;
    @Column(name = "instrument_no", nullable = false)
    private String instrumentNo;
    @Column(name = "programme_version_id", nullable = false)
    private UUID programmeVersionId;
    @Column(name = "module_id")
    private UUID moduleId;
    @Column(nullable = false)
    private String title;
    @Column(name = "assessment_type", nullable = false)
    private String assessmentType;
    @Column(name = "max_mark")
    private BigDecimal maxMark;
    private String guidance;
    @Column(name = "requires_moderation", nullable = false)
    private boolean requiresModeration = true;
    @Column(nullable = false)
    private String status = AssessmentRules.DRAFT;
    @Column(name = "approved_by")
    private UUID approvedBy;
    @Column(name = "approved_at")
    private Instant approvedAt;
    @Column(name = "approval_override_reason")
    private String approvalOverrideReason;
    @Column(name = "retired_reason")
    private String retiredReason;
    @Column(name = "retired_by")
    private UUID retiredBy;
    @Column(name = "retired_at")
    private Instant retiredAt;
    @Column(name = "created_by", nullable = false)
    private UUID createdBy;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
    @Version
    private Long version;

    public static TrainProvAssessmentInstrument create(TenantId t, String no, UUID programmeVersionId, UUID moduleId, String title, String type,
                                                       BigDecimal maxMark, String guidance, boolean requiresModeration, UUID by) {
        TrainProvAssessmentInstrument i = new TrainProvAssessmentInstrument();
        i.tenantId = t.getValue();
        i.instrumentNo = no;
        i.programmeVersionId = programmeVersionId;
        i.createdBy = by;
        i.createdAt = Instant.now();
        i.apply(moduleId, title, type, maxMark, guidance, requiresModeration);
        return i;
    }

    public void change(UUID moduleId, String title, String type, BigDecimal maxMark, String guidance, boolean requiresModeration) {
        String problem = AssessmentRules.editProblem(status);
        if (problem != null) {
            throw new IllegalStateException(problem);
        }
        apply(moduleId, title, type, maxMark, guidance, requiresModeration);
    }

    private void apply(UUID moduleId, String title, String type, BigDecimal maxMark, String guidance, boolean requiresModeration) {
        String problem = AssessmentRules.instrumentProblem(title, type, maxMark);
        if (problem != null) {
            throw new IllegalArgumentException(problem);
        }
        this.moduleId = moduleId;
        this.title = title.trim();
        this.assessmentType = type;
        this.maxMark = maxMark;
        this.guidance = guidance == null || guidance.isBlank() ? null : guidance.trim();
        this.requiresModeration = requiresModeration;
        this.updatedAt = Instant.now();
    }

    public void approve(UUID by, String overrideReason) {
        String problem = AssessmentRules.approvalProblem(status, by.equals(createdBy), overrideReason);
        if (problem != null) {
            throw new IllegalStateException(problem);
        }
        this.status = AssessmentRules.APPROVED;
        this.approvedBy = by;
        this.approvedAt = Instant.now();
        this.approvalOverrideReason = by.equals(createdBy) ? overrideReason.trim() : null;
        this.updatedAt = this.approvedAt;
    }

    public void retire(String reason, UUID by) {
        String problem = AssessmentRules.retireProblem(status, reason);
        if (problem != null) {
            throw new IllegalStateException(problem);
        }
        this.status = AssessmentRules.RETIRED;
        this.retiredReason = reason.trim();
        this.retiredBy = by;
        this.retiredAt = Instant.now();
        this.updatedAt = this.retiredAt;
    }
}
