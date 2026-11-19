package za.co.handyflow.platform.legalcompliance.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.legalcompliance.domain.model.BcPlan;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BcPlanRepository extends JpaRepository<BcPlan, UUID> {

    @Query("SELECT x FROM BcPlan x WHERE x.tenantId = :tenantId AND x.id = :id")
    Optional<BcPlan> findForTenant(TenantId tenantId, UUID id);

    @Query("SELECT x FROM BcPlan x WHERE x.tenantId = :tenantId AND x.processId = :processId ORDER BY x.createdAt DESC")
    List<BcPlan> findForProcess(TenantId tenantId, UUID processId);

    @Query("SELECT x FROM BcPlan x WHERE x.tenantId = :tenantId")
    List<BcPlan> findAllForTenant(TenantId tenantId);

    @Query("SELECT x FROM BcPlan x WHERE x.tenantId = :tenantId AND x.processId = :processId AND x.status = za.co.handyflow.platform.legalcompliance.domain.model.BcPlanStatus.APPROVED")
    Optional<BcPlan> findApprovedForProcess(TenantId tenantId, UUID processId);
}
