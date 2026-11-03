package za.co.handyflow.platform.documents.domain.repository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.documents.domain.model.StoredObject;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Platform-wide on purpose: the store and its check work across tenants. Every tenant-facing read checks the row's tenant itself. */
public interface StoredObjectRepository extends JpaRepository<StoredObject, UUID> {

    Optional<StoredObject> findByStorageKey(String storageKey);

    /** Live files, those checked longest ago (or never) first. */
    @Query("SELECT o.id FROM StoredObject o WHERE o.deletedAt IS NULL ORDER BY o.lastVerifiedAt ASC NULLS FIRST, o.createdAt ASC")
    List<UUID> findDue(Pageable page);

    /** [status, count] for live files. */
    @Query("SELECT o.verifyStatus, COUNT(o) FROM StoredObject o WHERE o.deletedAt IS NULL GROUP BY o.verifyStatus")
    List<Object[]> countLiveByStatus();

    @Query("SELECT COUNT(o) FROM StoredObject o WHERE o.deletedAt IS NULL AND o.encrypted = true")
    long countLiveEncrypted();

    @Query("SELECT MIN(o.lastVerifiedAt) FROM StoredObject o WHERE o.deletedAt IS NULL AND o.lastVerifiedAt IS NOT NULL")
    Optional<Instant> oldestCheck();

    @Query("""
            SELECT o FROM StoredObject o
            WHERE o.deletedAt IS NULL AND o.verifyStatus IN ('MISSING', 'UNREADABLE', 'MISMATCH')
            ORDER BY o.lastVerifiedAt DESC
            """)
    List<StoredObject> findFailing(Pageable page);
}
