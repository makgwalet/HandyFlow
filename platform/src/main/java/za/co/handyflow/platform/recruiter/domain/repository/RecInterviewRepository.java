package za.co.handyflow.platform.recruiter.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.recruiter.domain.model.RecInterview;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface RecInterviewRepository extends JpaRepository<RecInterview, UUID> {
    List<RecInterview> findByApplicationIdOrderByScheduledAtAsc(UUID applicationId);
    long countByRoundTemplateId(UUID roundTemplateId);

    /** Interviews still to happen that start inside a window, for finding clashes. Newest bookings of other companies are never returned. */
    @Query("""
        SELECT i FROM RecInterview i
        WHERE i.tenantId = :tenantId
        AND i.outcome = 'PENDING'
        AND i.scheduledAt IS NOT NULL
        AND i.scheduledAt > :after
        AND i.scheduledAt < :before
        """)
    List<RecInterview> findPendingStartingBetween(UUID tenantId, Instant after, Instant before);

    // Only PENDING interviews (a resolved outcome means the interview
    // already happened or was cancelled — no point reminding anyone)
    // whose scheduledAt is still in the future but inside the reminder
    // window, and hasn't already been reminded about.
    @Query("""
        SELECT i FROM RecInterview i
        WHERE i.scheduledAt IS NOT NULL
        AND i.scheduledAt > :now
        AND i.scheduledAt <= :windowEnd
        AND i.reminderSentAt IS NULL
        AND i.outcome = 'PENDING'
        """)
    List<RecInterview> findDueForReminder(Instant now, Instant windowEnd);
}