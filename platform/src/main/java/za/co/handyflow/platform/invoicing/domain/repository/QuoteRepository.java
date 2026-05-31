package za.co.handyflow.platform.invoicing.domain.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
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
}

