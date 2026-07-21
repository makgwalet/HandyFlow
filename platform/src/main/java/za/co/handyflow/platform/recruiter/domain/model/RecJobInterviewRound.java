package za.co.handyflow.platform.recruiter.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.util.UUID;

/**
 * A named, ordered round in a job's interview process — e.g. "Phone
 * Screen" (sequence 1), "Technical" (sequence 2), "Final" (sequence 3).
 * Defined per job, not shared across jobs (v1 — see migration comment for
 * why). RecInterview.roundTemplateId links a scheduled interview to one
 * of these; that link is optional, so ad-hoc interviews outside the
 * defined process are still possible.
 */
@Entity
@Table(name = "rec_job_interview_rounds")
@Getter
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class RecJobInterviewRound {

    @Id private UUID id = UUID.randomUUID();

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;

    @Column(name = "job_id", nullable = false) private UUID jobId;
    @Column(nullable = false) private String name;
    @Column(nullable = false) private int sequence;
    private String description;
    @Column(name = "created_at") private Instant createdAt;
    @Column(name = "updated_at") private Instant updatedAt;

    public static RecJobInterviewRound create(TenantId tenantId, UUID jobId, String name,
                                              int sequence, String description) {
        RecJobInterviewRound r = new RecJobInterviewRound();
        r.tenantId    = tenantId;
        r.jobId       = jobId;
        r.name        = name;
        r.sequence    = sequence;
        r.description = description;
        r.createdAt   = Instant.now();
        r.updatedAt   = Instant.now();
        return r;
    }

    public void update(String name, int sequence, String description) {
        if (name != null) this.name = name;
        this.sequence = sequence;
        this.description = description;
        this.updatedAt = Instant.now();
    }
}