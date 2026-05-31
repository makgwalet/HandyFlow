package za.co.handyflow.platform.expenses.domain.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.expenses.domain.model.ExpenseClaim;
import za.co.handyflow.platform.shared.TenantId;

import java.util.Optional;
import java.util.UUID;

public interface ExpenseClaimRepository extends JpaRepository<ExpenseClaim, UUID> {

    @Query("""
        SELECT c FROM ExpenseClaim c
        WHERE c.tenantId = :#{#tenantId.value}
        AND (:status IS NULL OR c.status = :status)
        AND (:employeeId IS NULL OR c.employeeId = :employeeId)
        ORDER BY c.claimDate DESC, c.createdAt DESC
        """)
    Page<ExpenseClaim> findAll(TenantId tenantId, String status,
                               UUID employeeId, Pageable pageable);

    @Query("SELECT c FROM ExpenseClaim c WHERE c.tenantId = :#{#tenantId.value} AND c.id = :id")
    Optional<ExpenseClaim> findByTenantAndId(TenantId tenantId, UUID id);

    @Query("SELECT COALESCE(SUM(c.amount),0) FROM ExpenseClaim c WHERE c.tenantId = :#{#tenantId.value} AND c.status IN ('APPROVED','REIMBURSED') AND EXTRACT(MONTH FROM c.claimDate) = :month AND EXTRACT(YEAR FROM c.claimDate) = :year")
    java.math.BigDecimal sumApprovedByMonth(TenantId tenantId, int month, int year);
}