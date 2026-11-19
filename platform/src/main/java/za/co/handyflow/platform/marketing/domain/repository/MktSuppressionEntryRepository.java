package za.co.handyflow.platform.marketing.domain.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import za.co.handyflow.platform.marketing.domain.model.MktSuppressionEntry;
import za.co.handyflow.platform.shared.TenantId;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MktSuppressionEntryRepository extends JpaRepository<MktSuppressionEntry, UUID> {

    @Query("""
        SELECT s FROM MktSuppressionEntry s
        WHERE s.tenantId = :tenantId AND s.channel = 'EMAIL'
          AND s.email = :email AND s.liftedAt IS NULL
        """)
    Optional<MktSuppressionEntry> findActive(@Param("tenantId") TenantId tenantId,
                                             @Param("email") String email);

    @Query("""
        SELECT s.email FROM MktSuppressionEntry s
        WHERE s.tenantId = :tenantId AND s.channel = 'EMAIL' AND s.liftedAt IS NULL
        """)
    List<String> findAllActiveEmails(@Param("tenantId") TenantId tenantId);

    @Query("""
        SELECT s.email FROM MktSuppressionEntry s
        WHERE s.tenantId = :tenantId AND s.channel = 'EMAIL' AND s.liftedAt IS NULL
          AND s.email IN :emails
        """)
    List<String> findActiveEmailsAmong(@Param("tenantId") TenantId tenantId,
                                       @Param("emails") Collection<String> emails);

    @Query("""
        SELECT s FROM MktSuppressionEntry s
        WHERE s.tenantId = :tenantId
        ORDER BY s.createdAt DESC
        """)
    Page<MktSuppressionEntry> findAllForTenant(@Param("tenantId") TenantId tenantId, Pageable pageable);

    @Query("""
        SELECT s FROM MktSuppressionEntry s
        WHERE s.tenantId = :tenantId AND s.liftedAt IS NULL
        ORDER BY s.createdAt DESC
        """)
    Page<MktSuppressionEntry> findActiveForTenant(@Param("tenantId") TenantId tenantId, Pageable pageable);

    @Query("""
        SELECT COUNT(s) FROM MktSuppressionEntry s
        WHERE s.tenantId = :tenantId AND s.liftedAt IS NULL
        """)
    long countActive(@Param("tenantId") TenantId tenantId);

    Optional<MktSuppressionEntry> findByIdAndTenantId(UUID id, TenantId tenantId);

    /**
     * Race-safe insert: two concurrent unsubscribes (e.g. a double-click or a mail
     * scanner prefetch) can't both create an active row — the partial unique index
     * makes the loser a no-op instead of an exception that would poison the transaction.
     */
    @Modifying
    @Query(value = """
        INSERT INTO mkt_suppression_entries
            (id, tenant_id, email, channel, reason, source, note, created_by, created_at)
        VALUES
            (:id, :tenantId, :email, 'EMAIL', :reason,
             CAST(:source AS varchar), CAST(:note AS text), CAST(:createdBy AS uuid), now())
        ON CONFLICT DO NOTHING
        """, nativeQuery = true)
    int insertIfAbsent(@Param("id") UUID id,
                       @Param("tenantId") UUID tenantId,
                       @Param("email") String email,
                       @Param("reason") String reason,
                       @Param("source") String source,
                       @Param("note") String note,
                       @Param("createdBy") UUID createdBy);
}