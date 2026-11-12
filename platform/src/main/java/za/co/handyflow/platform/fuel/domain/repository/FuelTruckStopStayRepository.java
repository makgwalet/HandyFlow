package za.co.handyflow.platform.fuel.domain.repository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.fuel.domain.model.FuelTruckStopStay;
import za.co.handyflow.platform.shared.TenantId;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FuelTruckStopStayRepository extends JpaRepository<FuelTruckStopStay, UUID> {

    @Query("SELECT s FROM FuelTruckStopStay s WHERE s.tenantId = :tenantId ORDER BY s.arrivalDate DESC, s.createdAt DESC")
    List<FuelTruckStopStay> findRecent(TenantId tenantId, Pageable page);

    @Query("SELECT s FROM FuelTruckStopStay s WHERE s.tenantId = :tenantId AND s.id = :id")
    Optional<FuelTruckStopStay> findOwned(TenantId tenantId, UUID id);

    /** Stays holding a bay (booked or in the yard) whose dates touch [from, to). In-yard stays are included even when planned to have left, so overstays count. */
    @Query("SELECT s FROM FuelTruckStopStay s WHERE s.tenantId = :tenantId AND s.status IN ('BOOKED','CHECKED_IN') AND s.arrivalDate < :to AND (s.departureDate > :from OR s.status = 'CHECKED_IN')")
    List<FuelTruckStopStay> findHoldingBays(TenantId tenantId, LocalDate from, LocalDate to);
}
