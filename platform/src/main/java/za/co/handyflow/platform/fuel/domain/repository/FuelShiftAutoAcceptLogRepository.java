package za.co.handyflow.platform.fuel.domain.repository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.fuel.domain.model.FuelShiftAutoAcceptLog;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.UUID;

public interface FuelShiftAutoAcceptLogRepository extends JpaRepository<FuelShiftAutoAcceptLog, UUID> {

    @Query("SELECT l FROM FuelShiftAutoAcceptLog l WHERE l.tenantId = :tenantId ORDER BY l.acceptedAt DESC")
    List<FuelShiftAutoAcceptLog> findRecent(TenantId tenantId, Pageable page);
}
