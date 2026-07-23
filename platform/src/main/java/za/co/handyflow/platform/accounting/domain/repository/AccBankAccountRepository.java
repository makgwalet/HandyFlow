package za.co.handyflow.platform.accounting.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.accounting.domain.model.AccBankAccount;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AccBankAccountRepository extends JpaRepository<AccBankAccount, UUID> {

    @Query("SELECT b FROM AccBankAccount b WHERE b.tenantId = :#{#tenantId.value} AND b.active = true AND b.deletedAt IS NULL ORDER BY b.bankName")
    List<AccBankAccount> findAllActive(TenantId tenantId);

    @Query("SELECT b FROM AccBankAccount b WHERE b.tenantId = :#{#tenantId.value} AND b.id = :id AND b.deletedAt IS NULL")
    Optional<AccBankAccount> findActiveById(TenantId tenantId, UUID id);

    // Cross-tenant, used by AccountingNotificationScheduler — no tenant
    // param since the scheduler runs once for the whole platform, same
    // pattern as AccVatPeriodRepository.findOpenPeriodsEndingBetween().
    @Query("""
        SELECT b FROM AccBankAccount b
        WHERE b.active = true AND b.deletedAt IS NULL
        AND b.lowBalanceThreshold IS NOT NULL
        AND b.currentBalance < b.lowBalanceThreshold
        """)
    List<AccBankAccount> findAllAccountsBelowThreshold();
}