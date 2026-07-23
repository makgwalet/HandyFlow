package za.co.handyflow.platform.accounting.domain.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.accounting.domain.model.AccBankTransaction;
import za.co.handyflow.platform.shared.TenantId;

import java.util.UUID;

public interface AccBankTransactionRepository extends JpaRepository<AccBankTransaction, UUID> {

    @Query("SELECT t FROM AccBankTransaction t WHERE t.tenantId = :#{#tenantId.value} AND t.bankAccountId = :bankAccountId ORDER BY t.transactionDate DESC")
    Page<AccBankTransaction> findByBankAccount(TenantId tenantId, UUID bankAccountId, Pageable pageable);

    @Query("SELECT t FROM AccBankTransaction t WHERE t.tenantId = :#{#tenantId.value} AND t.bankAccountId = :bankAccountId AND t.reconciled = false ORDER BY t.transactionDate")
    java.util.List<AccBankTransaction> findUnreconciled(TenantId tenantId, UUID bankAccountId);

    // Used by CSV import to skip rows that look like the same statement
    // line already imported before — same account, date, amount and
    // description. Not foolproof (two genuinely identical transactions on
    // the same day would also be skipped), but a reasonable default for a
    // generic importer with no bank-specific unique reference to key off.
    @Query("""
        SELECT COUNT(t) > 0 FROM AccBankTransaction t
        WHERE t.tenantId = :#{#tenantId.value}
        AND t.bankAccountId = :bankAccountId
        AND t.transactionDate = :transactionDate
        AND t.amount = :amount
        AND t.description = :description
        """)
    boolean existsDuplicate(TenantId tenantId, UUID bankAccountId,
                            java.time.LocalDate transactionDate,
                            java.math.BigDecimal amount, String description);

    // Journal line IDs already linked to some bank transaction — used to
    // exclude already-reconciled lines from match-candidate suggestions,
    // so the same journal line can't be matched to two different bank
    // transactions.
    @Query("""
        SELECT t.journalLineId FROM AccBankTransaction t
        WHERE t.tenantId = :#{#tenantId.value}
        AND t.journalLineId IS NOT NULL
        """)
    java.util.List<UUID> findLinkedJournalLineIds(TenantId tenantId);
}