package za.co.handyflow.platform.marketing.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.marketing.domain.model.MktSendQueue;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface MktSendQueueRepository extends JpaRepository<MktSendQueue, UUID> {

    // Pick up to 50 pending items ready to send — ordered by scheduled_at
    @Query("""
        SELECT q FROM MktSendQueue q
        WHERE q.status = 'PENDING'
        AND q.scheduledAt <= :now
        ORDER BY q.scheduledAt ASC
        """)
    List<MktSendQueue> findPendingBatch(Instant now, org.springframework.data.domain.Pageable pageable);

    // Failed items eligible for retry (not yet DEAD)
    @Query("""
        SELECT q FROM MktSendQueue q
        WHERE q.status = 'FAILED'
        AND q.retryCount < 3
        ORDER BY q.processedAt ASC
        """)
    List<MktSendQueue> findFailedForRetry(org.springframework.data.domain.Pageable pageable);

    long countByCampaignIdAndStatus(UUID campaignId, String status);
}
