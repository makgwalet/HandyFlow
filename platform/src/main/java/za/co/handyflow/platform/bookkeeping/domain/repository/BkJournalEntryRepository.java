package za.co.handyflow.platform.bookkeeping.domain.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import za.co.handyflow.platform.bookkeeping.domain.model.BkJournalEntry;
import za.co.handyflow.platform.shared.TenantId;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BkJournalEntryRepository extends JpaRepository<BkJournalEntry, UUID> {

    @Query("SELECT e FROM BkJournalEntry e WHERE e.tenantId = :#{#tenantId.value} AND e.id = :id AND e.deletedAt IS NULL")
    Optional<BkJournalEntry> findActiveById(@Param("tenantId") TenantId tenantId, @Param("id") UUID id);

    @Query("SELECT e FROM BkJournalEntry e WHERE e.tenantId = :#{#tenantId.value} AND e.clientId = :clientId AND e.deletedAt IS NULL ORDER BY e.entryDate DESC")
    Page<BkJournalEntry> findAllForClient(@Param("tenantId") TenantId tenantId, @Param("clientId") UUID clientId, Pageable pageable);

    @Query("SELECT e FROM BkJournalEntry e WHERE e.tenantId = :#{#tenantId.value} AND e.periodId = :periodId AND e.deletedAt IS NULL ORDER BY e.entryDate DESC")
    List<BkJournalEntry> findAllForPeriod(@Param("tenantId") TenantId tenantId, @Param("periodId") UUID periodId);

    /**
     * POSTED entries for a client within a date range — the pool
     * {@code BkBankTransactionService.getMatchCandidates} scans, iterating
     * each entry's own (eagerly-fetched) lines in-memory, since {@code
     * BkJournalLine} carries no {@code tenantId}/{@code clientId} of its
     * own to filter on directly. Mirrors {@code AccountingService}'s own
     * {@code journalRepo.findPostedInRange} in shape.
     */
    @Query("SELECT e FROM BkJournalEntry e WHERE e.tenantId = :#{#tenantId.value} AND e.clientId = :clientId " +
           "AND e.status = 'POSTED' AND e.deletedAt IS NULL AND e.entryDate BETWEEN :from AND :to ORDER BY e.entryDate DESC")
    List<BkJournalEntry> findPostedInRangeForClient(@Param("tenantId") TenantId tenantId, @Param("clientId") UUID clientId,
                                                     @Param("from") LocalDate from, @Param("to") LocalDate to);
}
