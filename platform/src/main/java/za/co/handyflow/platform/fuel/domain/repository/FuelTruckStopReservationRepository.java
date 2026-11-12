package za.co.handyflow.platform.fuel.domain.repository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.fuel.domain.model.FuelTruckStopReservation;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FuelTruckStopReservationRepository extends JpaRepository<FuelTruckStopReservation, UUID> {
    @Query("SELECT r FROM FuelTruckStopReservation r WHERE r.tenantId = :tenantId AND r.id = :id")
    Optional<FuelTruckStopReservation> findOwned(TenantId tenantId, UUID id);

    @Query("SELECT r FROM FuelTruckStopReservation r WHERE r.tenantId = :tenantId ORDER BY r.createdAt DESC")
    List<FuelTruckStopReservation> findRecent(TenantId tenantId, Pageable page);

    @Query("SELECT r FROM FuelTruckStopReservation r WHERE r.tenantId = :tenantId AND UPPER(r.vehicleRegistration) = UPPER(:registration) ORDER BY r.createdAt DESC")
    List<FuelTruckStopReservation> findByVehicle(TenantId tenantId, String registration, Pageable page);
}
