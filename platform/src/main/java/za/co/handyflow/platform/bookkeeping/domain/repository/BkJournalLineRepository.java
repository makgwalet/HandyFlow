package za.co.handyflow.platform.bookkeeping.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import za.co.handyflow.platform.bookkeeping.domain.model.BkJournalLine;
import za.co.handyflow.platform.shared.TenantId;

import java.util.Optional;
import java.util.UUID;

/**
 * {@code BkJournalLine} carries no {@code tenantId}/{@code clientId} of
 * its own (it belongs to a {@code BkJournalEntry}, which does) — every
 * tenant/client-scoped lookup here goes through the read-only {@code
 * journalEntry} association (mirrors {@code AccJournalLine}'s own shape:
 * lines are normally reached through their parent entry's cascade-managed
 * collection, and this repository exists only for the one place this
 * module needs a line directly — validating a {@code journalLineId} an
 * operator supplies when reconciling a bank transaction against an
 * existing line).
 */
public interface BkJournalLineRepository extends JpaRepository<BkJournalLine, UUID> {

    @Query("SELECT l FROM BkJournalLine l WHERE l.id = :id " +
           "AND l.journalEntry.tenantId = :#{#tenantId.value} AND l.journalEntry.clientId = :clientId " +
           "AND l.journalEntry.deletedAt IS NULL")
    Optional<BkJournalLine> findByIdForClient(@Param("tenantId") TenantId tenantId, @Param("clientId") UUID clientId,
                                               @Param("id") UUID id);
}
