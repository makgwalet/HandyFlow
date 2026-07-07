package za.co.handyflow.platform.invoicing.domain.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import za.co.handyflow.platform.invoicing.domain.model.Quote;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface QuoteRepository extends JpaRepository<Quote, UUID> {

    @Query("""
        SELECT q FROM Quote q
        WHERE q.tenantId = :tenantId
        AND q.deletedAt IS NULL
        ORDER BY q.createdAt DESC
        """)
    Page<Quote> findAllActive(TenantId tenantId, Pageable pageable);

    @Query("""
        SELECT q FROM Quote q
        WHERE q.tenantId = :tenantId
        AND q.id = :id
        AND q.deletedAt IS NULL
        """)
    Optional<Quote> findActiveById(TenantId tenantId, UUID id);

    @Query("""
        SELECT DISTINCT q FROM Quote q
        LEFT JOIN FETCH q.lineItems
        WHERE q.tenantId = :tenantId
        AND q.id = :id
        AND q.deletedAt IS NULL
        """)
    Optional<Quote> findActiveByIdWithLineItems(TenantId tenantId, UUID id);

    // WHY this query? Used by the scheduler.
    // Only SENT quotes expire — partial DB index matches this exactly.
    @Query("""
        SELECT q FROM Quote q
        WHERE q.status = 'SENT'
        AND q.expiresAt < :now
        AND q.deletedAt IS NULL
        """)
    List<Quote> findExpiredQuotes(Instant now);

    @Query("""
        SELECT COUNT(q) FROM Quote q
        WHERE q.tenantId = :tenantId
        """)
    long countAllByTenantId(TenantId tenantId);

    // 3-day warning window, gated by expiryReminderSentAt so the job only
    // ever fires once per quote (see V46 migration + Quote.markExpiryReminderSent()).
    @Query("""
        SELECT q FROM Quote q
        WHERE q.status = 'SENT'
        AND q.expiryReminderSentAt IS NULL
        AND q.expiresAt IS NOT NULL
        AND q.expiresAt <= :warningThreshold
        AND q.expiresAt > :now
        AND q.deletedAt IS NULL
        """)
    List<Quote> findQuotesNeedingExpiryReminder(@Param("warningThreshold") Instant warningThreshold,
                                                @Param("now") Instant now);

    // Deliberately no tenantId parameter — the caller (an unauthenticated
    // client) doesn't know or supply a tenant. The token itself IS the
    // authorization; it's a random UUID, functionally unguessable, and
    // unique across the whole table (see the partial unique index in V47).
    @Query("SELECT q FROM Quote q WHERE q.publicAccessToken = :token AND q.deletedAt IS NULL")
    java.util.Optional<Quote> findByPublicAccessToken(@Param("token") UUID token);
}

