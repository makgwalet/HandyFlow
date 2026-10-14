package za.co.handyflow.platform.accounting.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.accounting.domain.model.AccBankReconciliation;
import za.co.handyflow.platform.shared.TenantId;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AccBankReconciliationRepository extends JpaRepository<AccBankReconciliation, UUID> {

    @Query("SELECT r FROM AccBankReconciliation r WHERE r.tenantId = :#{#tenantId.value} AND r.bankAccountId = :bankAccountId ORDER BY r.periodEnd DESC, r.signedAt DESC")
    List<AccBankReconciliation> findByAccount(TenantId tenantId, UUID bankAccountId);

    @Query("SELECT r FROM AccBankReconciliation r WHERE r.tenantId = :#{#tenantId.value} AND r.id = :id")
    Optional<AccBankReconciliation> findByTenantAndId(TenantId tenantId, UUID id);

    /** The date the account is signed off through, or null when it has no live sign-off. */
    @Query("SELECT MAX(r.periodEnd) FROM AccBankReconciliation r WHERE r.tenantId = :#{#tenantId.value} AND r.bankAccountId = :bankAccountId AND r.status = 'SIGNED_OFF'")
    LocalDate signedOffThrough(TenantId tenantId, UUID bankAccountId);
}
