package za.co.handyflow.platform.fuel.domain.repository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.fuel.domain.model.FuelArrivalCheck;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.UUID;

public interface FuelArrivalCheckRepository extends JpaRepository<FuelArrivalCheck, UUID> {

    /** Newest first. Ask for a page of one to get the latest. */
    @Query("SELECT c FROM FuelArrivalCheck c WHERE c.tenantId = :tenantId AND c.deliveryId = :deliveryId ORDER BY c.checkedAt DESC")
    List<FuelArrivalCheck> latest(TenantId tenantId, UUID deliveryId, Pageable page);
}
