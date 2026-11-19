package za.co.handyflow.platform.marketing.domain.repository;

import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;
import za.co.handyflow.platform.marketing.domain.model.MktSendQueue;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface MktSendQueueRepository extends JpaRepository<MktSendQueue, UUID> {

    /**
     * Claims up to a page of due items with FOR UPDATE SKIP LOCKED
     * (lock.timeout = -2 is Hibernate's SKIP_LOCKED), so two app instances
     * never pick up the same row. Only items of campaigns in SENDING status
     * are eligible — paused/cancelled campaigns can't starve the queue head.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "-2"))
    @Query("""
        SELECT q FROM MktSendQueue q
        WHERE q.status = 'PENDING'
        AND q.scheduledAt <= :now
        AND q.campaignId IN (SELECT c.id FROM MktCampaign c WHERE c.status = 'SENDING')
        ORDER BY q.scheduledAt ASC
        """)
    List<MktSendQueue> findPendingBatch(@Param("now") Instant now, Pageable pageable);

    @Query("""
        SELECT q FROM MktSendQueue q
        WHERE q.status = 'FAILED'
        AND q.retryCount < 3
        ORDER BY q.processedAt ASC
        """)
    List<MktSendQueue> findFailedForRetry(Pageable pageable);

    long countByCampaignIdAndStatus(UUID campaignId, String status);
}