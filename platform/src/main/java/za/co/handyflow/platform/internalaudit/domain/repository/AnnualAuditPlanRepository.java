package za.co.handyflow.platform.internalaudit.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import za.co.handyflow.platform.internalaudit.domain.model.AnnualAuditPlan;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AnnualAuditPlanRepository extends JpaRepository<AnnualAuditPlan, UUID> {

    @Query("SELECT p FROM AnnualAuditPlan p WHERE p.tenantId = :tenantId AND p.id = :id")
    Optional<AnnualAuditPlan> findByTenantAndId(@Param("tenantId") UUID tenantId, @Param("id") UUID id);

    @Query("SELECT p FROM AnnualAuditPlan p WHERE p.tenantId = :tenantId ORDER BY p.planYear DESC")
    List<AnnualAuditPlan> findAllForTenant(@Param("tenantId") UUID tenantId);
}
