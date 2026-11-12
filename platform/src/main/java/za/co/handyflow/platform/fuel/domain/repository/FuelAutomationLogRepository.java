package za.co.handyflow.platform.fuel.domain.repository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.fuel.domain.model.FuelAutomationLog;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.UUID;

public interface FuelAutomationLogRepository extends JpaRepository<FuelAutomationLog, UUID> {

    @Query("SELECT l FROM FuelAutomationLog l WHERE l.tenantId = :tenantId ORDER BY l.ranAt DESC")
    List<FuelAutomationLog> findRecent(TenantId tenantId, Pageable page);
}
