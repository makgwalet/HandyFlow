// security/domain/repository/PayrollPeriodRepository.java
package za.co.handyflow.platform.security.domain.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.security.domain.model.PayrollPeriod;
import za.co.handyflow.platform.shared.TenantId;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PayrollPeriodRepository extends JpaRepository<PayrollPeriod, UUID> {

    @Query("SELECT p FROM PayrollPeriod p WHERE p.tenantId = :tenantId AND p.id = :id")
    Optional<PayrollPeriod> findByTenantAndId(TenantId tenantId, UUID id);

    @Query("SELECT p FROM PayrollPeriod p WHERE p.tenantId = :tenantId ORDER BY p.periodStart DESC")
    Page<PayrollPeriod> findByTenant(TenantId tenantId, Pageable pageable);

    @Query("SELECT p FROM PayrollPeriod p WHERE p.tenantId = :tenantId AND p.status = 'DRAFT' ORDER BY p.periodStart DESC")
    List<PayrollPeriod> findDraftsByTenant(TenantId tenantId);

    @Query("""
        SELECT COUNT(p) > 0 FROM PayrollPeriod p
        WHERE p.tenantId = :tenantId
        AND p.status NOT IN ('PAID')
        AND p.periodStart <= :periodEnd
        AND p.periodEnd >= :periodStart
        AND (:branchId IS NULL OR p.branchId = :branchId OR p.branchId IS NULL)
        """)
    boolean hasOverlappingPeriod(TenantId tenantId, LocalDate periodStart, LocalDate periodEnd, UUID branchId);
}