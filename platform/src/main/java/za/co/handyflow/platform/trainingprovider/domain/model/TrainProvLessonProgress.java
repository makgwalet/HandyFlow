package za.co.handyflow.platform.trainingprovider.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;
import za.co.handyflow.platform.trainingprovider.domain.rules.LessonRules;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** A learner finished a lesson. Progress only. Reversed with a reason, never deleted. */
@Entity(name = "TrainProvLessonProgress")
@Table(name = "trainprov_lesson_progress")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TrainProvLessonProgress {
    @Id
    private UUID id = UUID.randomUUID();
    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;
    @Column(name = "enrolment_id", nullable = false, updatable = false)
    private UUID enrolmentId;
    @Column(name = "lesson_id", nullable = false, updatable = false)
    private UUID lessonId;
    @Column(name = "finished_on", nullable = false)
    private LocalDate finishedOn;
    private String note;
    @Column(nullable = false)
    private String status = LessonRules.FINISHED;
    @Column(name = "reversed_reason")
    private String reversedReason;
    @Column(name = "reversed_by")
    private UUID reversedBy;
    @Column(name = "reversed_at")
    private Instant reversedAt;
    @Column(name = "recorded_by", nullable = false)
    private UUID recordedBy;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
    @Version
    private Long version;

    public static TrainProvLessonProgress finish(TenantId t, UUID enrolmentId, UUID lessonId, LocalDate on, String note, UUID by) {
        TrainProvLessonProgress p = new TrainProvLessonProgress();
        p.tenantId = t.getValue();
        p.enrolmentId = enrolmentId;
        p.lessonId = lessonId;
        p.finishedOn = on;
        p.note = note == null || note.isBlank() ? null : note.trim();
        p.recordedBy = by;
        p.createdAt = Instant.now();
        p.updatedAt = p.createdAt;
        return p;
    }

    public void reverse(String reason, UUID by) {
        String problem = LessonRules.reverseProblem(status, reason);
        if (problem != null) {
            throw new IllegalStateException(problem);
        }
        this.status = LessonRules.REVERSED;
        this.reversedReason = reason.trim();
        this.reversedBy = by;
        this.reversedAt = Instant.now();
        this.updatedAt = this.reversedAt;
    }
}
