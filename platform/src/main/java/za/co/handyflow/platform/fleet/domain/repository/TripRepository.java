// fleet/domain/repository/TripRepository.java

package za.co.handyflow.platform.fleet.domain.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.fleet.domain.model.Trip;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TripRepository extends JpaRepository<Trip, UUID> {

    @Query("SELECT t FROM Trip t WHERE t.vehicleId = :vehicleId ORDER BY t.startAt DESC")
    Page<Trip> findByVehicle(UUID vehicleId, Pageable pageable);

    @Query("SELECT t FROM Trip t WHERE t.vehicleId = :vehicleId AND t.endAt IS NULL")
    Optional<Trip> findActiveTrip(UUID vehicleId);

    @Query("SELECT t FROM Trip t WHERE t.tenantId = :tenantId ORDER BY t.startAt DESC")
    Page<Trip> findAllActive(TenantId tenantId, Pageable pageable);

    @Query("SELECT t FROM Trip t WHERE t.tenantId = :tenantId AND t.status = :status ORDER BY t.startAt DESC")
    Page<Trip> findAllActiveByStatus(TenantId tenantId, String status, Pageable pageable);

    @Query("SELECT t FROM Trip t WHERE t.status = 'ACTIVE' AND t.startAt < :cutoff AND t.longRunningAlertSent = false")
    List<Trip> findLongRunningUnalertedTrips(Instant cutoff);

    // NEW: backs the SARS logbook export — only COMPLETED trips have the
    // endOdometer/endAt/distanceKm a logbook needs; ACTIVE (still running)
    // or CANCELLED trips would either have nulls where SARS expects numbers
    // or represent journeys that never actually happened.
    @Query("SELECT t FROM Trip t WHERE t.vehicleId = :vehicleId AND t.status = 'COMPLETED' " +
            "AND t.startAt >= :from AND t.startAt < :to ORDER BY t.startAt ASC")
    List<Trip> findCompletedInRange(UUID vehicleId, Instant from, Instant to);
}
