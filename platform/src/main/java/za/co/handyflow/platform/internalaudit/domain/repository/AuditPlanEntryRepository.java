package za.co.handyflow.platform.internalaudit.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import za.co.handyflow.platform.internalaudit.domain.model.AuditPlanEntry;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AuditPlanEntryRepository extends JpaRepository<AuditPlanEntry, UUID> {

    @Query("SELECT e FROM AuditPlanEntry e WHERE e.tenantId = :tenantId AND e.id = :id")
    Optional<AuditPlanEntry> findByTenantAndId(@Param("tenantId") UUID tenantId, @Param("id") UUID id);

    @Query("SELECT e FROM AuditPlanEntry e WHERE e.tenantId = :tenantId AND e.planId = :planId ORDER BY e.plannedQuarter")
    List<AuditPlanEntry> findByPlan(@Param("tenantId") UUID tenantId, @Param("planId") UUID planId);
}
