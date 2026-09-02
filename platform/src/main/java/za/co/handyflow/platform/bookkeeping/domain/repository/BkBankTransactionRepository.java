package za.co.handyflow.platform.bookkeeping.domain.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import za.co.handyflow.platform.bookkeeping.domain.model.BkBankTransaction;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Direct mirror of the real {@code accounting.AccBankTransactionRepository}
 * (quoted in full in this module's build brief), adapted with a {@code
 * clientId} param throughout — {@code BkBankTransaction} has no {@code
 * deletedAt} of its own, matching {@code AccBankTransaction}'s own bare
 * shape.
 */
public interface BkBankTransactionRepository extends JpaRepository<BkBankTransaction, UUID> {

    @Query("SELECT t FROM BkBankTransaction t WHERE t.tenantId = :#{#tenantId.value} AND t.clientId = :clientId " +
           "AND t.bankAccountId = :bankAccountId ORDER BY t.transactionDate DESC")
    Page<BkBankTransaction> findByBankAccount(@Param("tenantId") TenantId tenantId, @Param("clientId") UUID clientId,
                                               @Param("bankAccountId") UUID bankAccountId, Pageable pageable);

    @Query("SELECT t FROM BkBankTransaction t WHERE t.tenantId = :#{#tenantId.value} AND t.clientId = :clientId " +
           "AND t.bankAccountId = :bankAccountId AND t.reconciled = false ORDER BY t.transactionDate")
    List<BkBankTransaction> findUnreconciled(@Param("tenantId") TenantId tenantId, @Param("clientId") UUID clientId,
                                              @Param("bankAccountId") UUID bankAccountId);

    @Query("SELECT COUNT(t) > 0 FROM BkBankTransaction t WHERE t.tenantId = :#{#tenantId.value} AND t.clientId = :clientId " +
           "AND t.bankAccountId = :bankAccountId AND t.transactionDate = :transactionDate AND t.amount = :amount AND t.description = :description")
    boolean existsDuplicate(@Param("tenantId") TenantId tenantId, @Param("clientId") UUID clientId,
                             @Param("bankAccountId") UUID bankAccountId, @Param("transactionDate") LocalDate transactionDate,
                             @Param("amount") BigDecimal amount, @Param("description") String description);

    @Query("SELECT t.journalLineId FROM BkBankTransaction t WHERE t.tenantId = :#{#tenantId.value} AND t.clientId = :clientId AND t.journalLineId IS NOT NULL")
    List<UUID> findLinkedJournalLineIds(@Param("tenantId") TenantId tenantId, @Param("clientId") UUID clientId);

    @Query("SELECT t FROM BkBankTransaction t WHERE t.tenantId = :#{#tenantId.value} AND t.clientId = :clientId ORDER BY t.transactionDate DESC")
    Page<BkBankTransaction> findAllForClient(@Param("tenantId") TenantId tenantId, @Param("clientId") UUID clientId, Pageable pageable);

    /**
     * Cross-tenant sweep for the daily notification scheduler: transactions
     * still unreconciled after {@code cutoff} — a stale-reconciliation risk
     * flag for a bookkeeping practice (the whole point of the practice is
     * to keep a client's bank feed current).
     */
    @Query("SELECT t FROM BkBankTransaction t WHERE t.reconciled = false AND t.transactionDate <= :cutoff")
    List<BkBankTransaction> findUnreconciledOlderThan(@Param("cutoff") LocalDate cutoff);
}
