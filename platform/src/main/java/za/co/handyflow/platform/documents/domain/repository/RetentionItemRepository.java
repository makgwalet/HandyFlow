package za.co.handyflow.platform.documents.domain.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import za.co.handyflow.platform.documents.domain.model.RetentionItem;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Every query names the tenant. */
public interface RetentionItemRepository extends JpaRepository<RetentionItem, UUID> {

    @Query("""
            SELECT i FROM RetentionItem i
            WHERE i.tenantId = :tenantId AND i.moduleCode = :module AND i.sourceType = :sourceType AND i.sourceId = :sourceId
            """)
    Optional<RetentionItem> findBySource(@Param("tenantId") UUID tenantId, @Param("module") String module,
                                         @Param("sourceType") String sourceType, @Param("sourceId") UUID sourceId);

    @Query("""
            SELECT i FROM RetentionItem i
            WHERE i.tenantId = :tenantId AND i.moduleCode = :module AND i.sourceType = :sourceType AND i.sourceId IN :sourceIds
            """)
    List<RetentionItem> findBySources(@Param("tenantId") UUID tenantId, @Param("module") String module,
                                      @Param("sourceType") String sourceType, @Param("sourceIds") Collection<UUID> sourceIds);

    @Query("SELECT i FROM RetentionItem i WHERE i.tenantId = :tenantId AND i.id = :id")
    Optional<RetentionItem> findOwned(@Param("tenantId") UUID tenantId, @Param("id") UUID id);

    /** Locked until the transaction ends, so a hold, a proposal and a disposal of the same record are serialised. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT i FROM RetentionItem i WHERE i.tenantId = :tenantId AND i.id = :id")
    Optional<RetentionItem> findOwnedForUpdate(@Param("tenantId") UUID tenantId, @Param("id") UUID id);

    @Query("""
            SELECT i FROM RetentionItem i
            WHERE i.tenantId = :tenantId AND i.status = 'ACTIVE' AND i.legalHold = false
              AND i.retainUntil IS NOT NULL AND i.retainUntil < :today
            ORDER BY i.retainUntil, i.moduleCode, i.reference
            """)
    List<RetentionItem> findDue(@Param("tenantId") UUID tenantId, @Param("today") LocalDate today, Pageable page);

    @Query("""
            SELECT i FROM RetentionItem i
            WHERE i.tenantId = :tenantId AND i.status = 'ACTIVE' AND i.legalHold = false
              AND i.retainUntil IS NOT NULL AND i.retainUntil >= :today AND i.retainUntil <= :until
            ORDER BY i.retainUntil, i.moduleCode, i.reference
            """)
    List<RetentionItem> findUpcoming(@Param("tenantId") UUID tenantId, @Param("today") LocalDate today, @Param("until") LocalDate until, Pageable page);

    @Query("""
            SELECT i FROM RetentionItem i
            WHERE i.tenantId = :tenantId AND i.legalHold = true AND i.status <> 'DISPOSED'
            ORDER BY i.holdAt DESC
            """)
    List<RetentionItem> findHeld(@Param("tenantId") UUID tenantId, Pageable page);

    @Query("SELECT i FROM RetentionItem i WHERE i.tenantId = :tenantId AND i.status = 'DISPOSED' ORDER BY i.disposedAt DESC")
    List<RetentionItem> findDisposed(@Param("tenantId") UUID tenantId, Pageable page);

    /** Everything still kept, soonest date first (clock not started last). For the "all records" view. */
    @Query("""
            SELECT i FROM RetentionItem i
            WHERE i.tenantId = :tenantId AND i.status <> 'DISPOSED'
            ORDER BY i.retainUntil ASC NULLS LAST, i.createdAt DESC
            """)
    List<RetentionItem> findKept(@Param("tenantId") UUID tenantId, Pageable page);

    @Query("""
            SELECT i FROM RetentionItem i
            WHERE i.tenantId = :tenantId AND i.status <> 'DISPOSED' AND i.moduleCode = :module
            ORDER BY i.retainUntil ASC NULLS LAST, i.createdAt DESC
            """)
    List<RetentionItem> findKeptForModule(@Param("tenantId") UUID tenantId, @Param("module") String module, Pageable page);

    @Query("SELECT i FROM RetentionItem i WHERE i.tenantId = :tenantId AND i.id IN :ids")
    List<RetentionItem> findOwnedIn(@Param("tenantId") UUID tenantId, @Param("ids") Collection<UUID> ids);

    /** Items whose period may change when a policy of this module changes. */
    @Query("""
            SELECT i FROM RetentionItem i
            WHERE i.tenantId = :tenantId AND i.moduleCode = :module AND i.status <> 'DISPOSED' AND i.clockStart IS NOT NULL
            """)
    List<RetentionItem> findClockedForModule(@Param("tenantId") UUID tenantId, @Param("module") String module);

    /** [module, category, jurisdiction, count] of records still kept: the categories a policy screen must show. */
    @Query("""
            SELECT i.moduleCode, i.category, i.jurisdiction, COUNT(i) FROM RetentionItem i
            WHERE i.tenantId = :tenantId AND i.status <> 'DISPOSED'
            GROUP BY i.moduleCode, i.category, i.jurisdiction
            ORDER BY i.moduleCode, i.category, i.jurisdiction
            """)
    List<Object[]> countKeptByCategory(@Param("tenantId") UUID tenantId);

    @Query("SELECT i.status, COUNT(i) FROM RetentionItem i WHERE i.tenantId = :tenantId GROUP BY i.status")
    List<Object[]> countByStatus(@Param("tenantId") UUID tenantId);

    @Query("SELECT COUNT(i) FROM RetentionItem i WHERE i.tenantId = :tenantId AND i.legalHold = true AND i.status <> 'DISPOSED'")
    long countHeld(@Param("tenantId") UUID tenantId);

    @Query("""
            SELECT COUNT(i) FROM RetentionItem i
            WHERE i.tenantId = :tenantId AND i.status = 'ACTIVE' AND i.legalHold = false
              AND i.retainUntil IS NOT NULL AND i.retainUntil < :today
            """)
    long countDue(@Param("tenantId") UUID tenantId, @Param("today") LocalDate today);

    @Query("""
            SELECT COUNT(i) FROM RetentionItem i
            WHERE i.tenantId = :tenantId AND i.status = 'ACTIVE' AND i.legalHold = false
              AND i.retainUntil IS NOT NULL AND i.retainUntil >= :today AND i.retainUntil <= :until
            """)
    long countUpcoming(@Param("tenantId") UUID tenantId, @Param("today") LocalDate today, @Param("until") LocalDate until);

    @Query("SELECT COUNT(i) FROM RetentionItem i WHERE i.tenantId = :tenantId AND i.status <> 'DISPOSED' AND i.clockStart IS NULL")
    long countClockNotStarted(@Param("tenantId") UUID tenantId);
}
