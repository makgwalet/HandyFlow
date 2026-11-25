package za.co.handyflow.platform.trainingprovider.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;
import za.co.handyflow.platform.trainingprovider.domain.rules.AssessmentRules;
import za.co.handyflow.platform.trainingprovider.domain.rules.LessonRules;

import java.time.Instant;
import java.util.UUID;

/** A lesson in a programme version. Approved lessons are never edited: they are retired and replaced. */
@Entity(name = "TrainProvLesson")
@Table(name = "trainprov_lessons")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TrainProvLesson {
    @Id
    private UUID id = UUID.randomUUID();
    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;
    @Column(name = "lesson_no", nullable = false)
    private String lessonNo;
    @Column(name = "programme_version_id", nullable = false)
    private UUID programmeVersionId;
    @Column(name = "module_id")
    private UUID moduleId;
    @Column(nullable = false)
    private String title;
    @Column(nullable = false)
    private String kind;
    @Column(name = "content_text")
    private String contentText;
    private String url;
    @Column(name = "position_no", nullable = false)
    private int positionNo = 1;
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

    public static TrainProvLesson create(TenantId t, String no, UUID versionId, UUID moduleId, String title, String kind, String contentText, String url, Integer position, UUID by) {
        TrainProvLesson l = new TrainProvLesson();
        l.tenantId = t.getValue();
        l.lessonNo = no;
        l.programmeVersionId = versionId;
        l.createdBy = by;
        l.createdAt = Instant.now();
        l.apply(moduleId, title, kind, contentText, url, position);
        return l;
    }

    public void change(UUID moduleId, String title, String kind, String contentText, String url, Integer position) {
        String problem = AssessmentRules.editProblem(status);
        if (problem != null) {
            throw new IllegalStateException(problem);
        }
        apply(moduleId, title, kind, contentText, url, position);
    }

    private void apply(UUID moduleId, String title, String kind, String contentText, String url, Integer position) {
        String problem = LessonRules.lessonProblem(title, kind, contentText, url, position);
        if (problem != null) {
            throw new IllegalArgumentException(problem);
        }
        this.moduleId = moduleId;
        this.title = title.trim();
        this.kind = kind;
        boolean reading = LessonRules.READING.equals(kind);
        this.contentText = reading ? contentText.trim() : null;
        this.url = reading ? null : url.trim();
        this.positionNo = position == null ? 1 : position;
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
