package za.co.handyflow.platform.debtcollection.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.debtcollection.domain.model.PaymentPlan;
import za.co.handyflow.platform.shared.TenantId;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PaymentPlanRepository extends JpaRepository<PaymentPlan, UUID> {

    @Query("""
        SELECT p FROM PaymentPlan p WHERE p.tenantId = :#{#tenantId.value} AND p.caseId = :caseId
        ORDER BY p.createdAt DESC
        """)
    List<PaymentPlan> findByCaseId(TenantId tenantId, UUID caseId);

    @Query("SELECT p FROM PaymentPlan p WHERE p.tenantId = :#{#tenantId.value} AND p.id = :id")
    Optional<PaymentPlan> findByTenantIdAndId(TenantId tenantId, UUID id);

    @Query("""
        SELECT p FROM PaymentPlan p WHERE p.tenantId = :#{#tenantId.value} AND p.caseId = :caseId
        AND p.status = 'ACTIVE'
        """)
    Optional<PaymentPlan> findActiveByCaseId(TenantId tenantId, UUID caseId);

    /** Cross-tenant sweep — installment due within the window, used by the notification scheduler and by default-detection. */
    @Query("SELECT p FROM PaymentPlan p WHERE p.status = 'ACTIVE' AND p.nextDueDate BETWEEN :from AND :to")
    List<PaymentPlan> findActiveWithInstallmentDueWithinAcrossTenants(LocalDate from, LocalDate to);

    /** Cross-tenant sweep — overdue installments (nextDueDate before today, still ACTIVE) for default detection. */
    @Query("SELECT p FROM PaymentPlan p WHERE p.status = 'ACTIVE' AND p.nextDueDate < :today")
    List<PaymentPlan> findActiveOverdueAcrossTenants(LocalDate today);
}
