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
}