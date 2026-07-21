package za.co.handyflow.platform.recruiter.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "rec_interviews")
@Getter
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class RecInterview {

    @Id private UUID id = UUID.randomUUID();

    @Column(name = "application_id", nullable = false) private UUID    applicationId;
    @Column(name = "tenant_id",      nullable = false) private UUID    tenantId;
    @Column(name = "interview_type", nullable = false) private String  interviewType = "VIDEO";
    @Column(name = "scheduled_at")                      private Instant scheduledAt;
    @Column(name = "interviewer_id")                    private UUID    interviewerId;
    @Column(name = "interviewer_name")                  private String  interviewerName;
    private String  outcome = "PENDING";
    private String  notes;
    private Integer score;
    // Venue address (IN_PERSON/PANEL) or meeting link (VIDEO — Google Meet/
    // Teams/Zoom/etc.). One free-text field rather than two separate
    // columns since exactly one of "where" or "how to join" applies per
    // interview_type, never both.
    private String  location;
    @Column(name = "round_template_id") private UUID roundTemplateId;
    @Column(name = "reminder_sent_at")  private Instant reminderSentAt;
    // Set on the OLD row when it's superseded by a reschedule — the
    // reason a reschedule happened, distinct from "notes" (which is for
    // the interviewer's actual assessment after conducting an interview
    // that DID happen; a rescheduled one never did).
    @Column(name = "reschedule_reason") private String rescheduleReason;
    // Set on the NEW row, pointing back to what it replaced — lets the
    // full reschedule chain be traced from either end.
    @Column(name = "rescheduled_from_interview_id") private UUID rescheduledFromInterviewId;
    @Column(name = "created_at") private Instant createdAt;

    public static RecInterview create(UUID applicationId, UUID tenantId,
                                      String interviewType, Instant scheduledAt,
                                      UUID interviewerId, String interviewerName,
                                      String location, UUID roundTemplateId) {
        return create(applicationId, tenantId, interviewType, scheduledAt,
                interviewerId, interviewerName, location, roundTemplateId, null);
    }

    public static RecInterview create(UUID applicationId, UUID tenantId,
                                      String interviewType, Instant scheduledAt,
                                      UUID interviewerId, String interviewerName,
                                      String location, UUID roundTemplateId,
                                      UUID rescheduledFromInterviewId) {
        RecInterview i      = new RecInterview();
        i.applicationId     = applicationId;
        i.tenantId          = tenantId;
        i.interviewType     = interviewType != null ? interviewType : "VIDEO";
        i.scheduledAt       = scheduledAt;
        i.interviewerId     = interviewerId;
        i.interviewerName   = interviewerName;
        i.location          = location;
        i.roundTemplateId   = roundTemplateId;
        i.rescheduledFromInterviewId = rescheduledFromInterviewId;
        i.outcome           = "PENDING";
        i.createdAt         = Instant.now();
        return i;
    }

    public void recordOutcome(String outcome, String notes, Integer score) {
        this.outcome = outcome;
        this.notes   = notes;
        this.score   = score;
    }

    public void markReminderSent() {
        this.reminderSentAt = Instant.now();
    }

    // Closes out THIS row as superseded — notes/score deliberately left
    // untouched, since this interview never actually happened.
    public void markRescheduled(String reason) {
        this.outcome = "RESCHEDULED";
        this.rescheduleReason = reason;
    }
}