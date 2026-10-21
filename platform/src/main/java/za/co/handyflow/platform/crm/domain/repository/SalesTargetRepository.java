package za.co.handyflow.platform.crm.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import za.co.handyflow.platform.crm.domain.model.SalesTarget;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SalesTargetRepository extends JpaRepository<SalesTarget, UUID> {

    @Query("SELECT t FROM SalesTarget t WHERE t.tenantId = :tenantId AND t.period = :period")
    List<SalesTarget> findByPeriod(@Param("tenantId") TenantId tenantId, @Param("period") String period);

    @Query("SELECT t FROM SalesTarget t WHERE t.tenantId = :tenantId AND t.ownerId = :ownerId AND t.period = :period")
    Optional<SalesTarget> findOne(@Param("tenantId") TenantId tenantId, @Param("ownerId") UUID ownerId, @Param("period") String period);
}
