package za.co.handyflow.platform.fuel.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.fuel.domain.model.FuelDeliveryCompartment;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.UUID;

public interface FuelDeliveryCompartmentRepository extends JpaRepository<FuelDeliveryCompartment, UUID> {

    @Query("SELECT d FROM FuelDeliveryCompartment d WHERE d.tenantId = :tenantId AND d.deliveryId = :deliveryId")
    List<FuelDeliveryCompartment> findByDelivery(TenantId tenantId, UUID deliveryId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("DELETE FROM FuelDeliveryCompartment d WHERE d.tenantId = :tenantId AND d.deliveryId = :deliveryId")
    void deleteByDelivery(TenantId tenantId, UUID deliveryId);
}
