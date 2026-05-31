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
    @Column(name = "created_at") private Instant createdAt;

    public static RecInterview create(UUID applicationId, UUID tenantId,
                                       String interviewType, Instant scheduledAt,
                                       UUID interviewerId, String interviewerName) {
        RecInterview i      = new RecInterview();
        i.applicationId     = applicationId;
        i.tenantId          = tenantId;
        i.interviewType     = interviewType != null ? interviewType : "VIDEO";
        i.scheduledAt       = scheduledAt;
        i.interviewerId     = interviewerId;
        i.interviewerName   = interviewerName;
        i.outcome           = "PENDING";
        i.createdAt         = Instant.now();
        return i;
    }

    public void recordOutcome(String outcome, String notes, Integer score) {
        this.outcome = outcome;
        this.notes   = notes;
        this.score   = score;
    }
}
