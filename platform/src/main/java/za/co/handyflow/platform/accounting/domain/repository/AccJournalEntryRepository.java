package za.co.handyflow.platform.accounting.domain.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.accounting.domain.model.AccJournalEntry;
import za.co.handyflow.platform.shared.TenantId;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AccJournalEntryRepository extends JpaRepository<AccJournalEntry, UUID> {

    @Query("""
        SELECT e FROM AccJournalEntry e
        WHERE e.tenantId = :#{#tenantId.value} AND e.deletedAt IS NULL
        AND (:status IS NULL OR e.status = :status)
        ORDER BY e.entryDate DESC
        """)
    Page<AccJournalEntry> findAllActive(TenantId tenantId, String status, Pageable pageable);

    @Query("SELECT e FROM AccJournalEntry e WHERE e.tenantId = :#{#tenantId.value} AND e.id = :id AND e.deletedAt IS NULL")
    Optional<AccJournalEntry> findActiveById(TenantId tenantId, UUID id);

    @Query("SELECT e FROM AccJournalEntry e WHERE e.tenantId = :#{#tenantId.value} AND e.entryDate BETWEEN :from AND :to AND e.status = 'POSTED' AND e.deletedAt IS NULL ORDER BY e.entryDate")
    List<AccJournalEntry> findPostedInRange(TenantId tenantId, LocalDate from, LocalDate to);

    @Query("SELECT COUNT(e) FROM AccJournalEntry e WHERE e.tenantId = :#{#tenantId.value}")
    long countByTenant(TenantId tenantId);
}