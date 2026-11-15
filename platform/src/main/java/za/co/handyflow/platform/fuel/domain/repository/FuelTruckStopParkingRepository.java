package za.co.handyflow.platform.fuel.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.fuel.domain.model.FuelTruckStopParking;
import za.co.handyflow.platform.shared.TenantId;

import java.util.Optional;
import java.util.UUID;

public interface FuelTruckStopParkingRepository extends JpaRepository<FuelTruckStopParking, UUID> {

    @Query("SELECT p FROM FuelTruckStopParking p WHERE p.tenantId = :tenantId")
    Optional<FuelTruckStopParking> findForTenant(TenantId tenantId);

    /**
     * Takes a row lock on the company's parking settings for the rest of the transaction. Booking and extending a stay take it
     * first, so two requests for the last bay are handled one after the other and the second sees the first.
     */
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM FuelTruckStopParking p WHERE p.tenantId = :tenantId")
    Optional<FuelTruckStopParking> lockForTenant(TenantId tenantId);
}
