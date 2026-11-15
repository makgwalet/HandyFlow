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

    /** What a compartment last carried: its most recent load that has left the depot (in transit or delivered), as of the query. Ties are resolved by the caller. */
    @Query("""
            SELECT new za.co.handyflow.platform.fuel.domain.repository.FuelDeliveryCompartmentRepository$PreviousCargo(dc.compartmentId, d.fuelType, COALESCE(d.deliveredAt, d.loadedAt, d.scheduledAt))
            FROM FuelDeliveryCompartment dc, FuelDelivery d
            WHERE dc.tenantId = :tenantId AND d.tenantId = :tenantId AND d.id = dc.deliveryId AND dc.compartmentId IN :ids
              AND d.id <> :excludeDeliveryId AND d.deletedAt IS NULL AND d.status IN ('IN_TRANSIT', 'DELIVERED')
              AND COALESCE(d.deliveredAt, d.loadedAt, d.scheduledAt) = (
                  SELECT MAX(COALESCE(d2.deliveredAt, d2.loadedAt, d2.scheduledAt)) FROM FuelDeliveryCompartment dc2, FuelDelivery d2
                  WHERE dc2.tenantId = :tenantId AND d2.tenantId = :tenantId AND d2.id = dc2.deliveryId AND dc2.compartmentId = dc.compartmentId
                    AND d2.id <> :excludeDeliveryId AND d2.deletedAt IS NULL AND d2.status IN ('IN_TRANSIT', 'DELIVERED'))
            """)
    List<PreviousCargo> findPreviousCargo(TenantId tenantId, java.util.Collection<UUID> ids, UUID excludeDeliveryId);

    record PreviousCargo(UUID compartmentId, String fuelType, java.time.Instant at) {}

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("DELETE FROM FuelDeliveryCompartment d WHERE d.tenantId = :tenantId AND d.deliveryId = :deliveryId")
    void deleteByDelivery(TenantId tenantId, UUID deliveryId);
}
