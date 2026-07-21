package za.co.handyflow.platform.recruiter.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * Additional interviewers on a panel interview — alongside, not instead
 * of, RecInterview's existing single interviewerId/interviewerName (kept
 * as the "primary interviewer" per the explicit decision to keep both
 * concepts rather than fold panelists into a single list).
 */
@Entity
@Table(name = "rec_interview_panelists")
@Getter
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class RecInterviewPanelist {

    @Id private UUID id = UUID.randomUUID();

    @Column(name = "interview_id", nullable = false) private UUID interviewId;
    @Column(name = "user_id", nullable = false)      private UUID userId;
    @Column(name = "user_name")                       private String userName;
    @Column(name = "created_at") private Instant createdAt;

    public static RecInterviewPanelist create(UUID interviewId, UUID userId, String userName) {
        RecInterviewPanelist p = new RecInterviewPanelist();
        p.interviewId = interviewId;
        p.userId      = userId;
        p.userName    = userName;
        p.createdAt   = Instant.now();
        return p;
    }
}