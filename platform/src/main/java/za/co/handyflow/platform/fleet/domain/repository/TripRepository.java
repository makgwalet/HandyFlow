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

    @Query("SELECT t FROM Trip t WHERE t.vehicleId = :vehicleId AND t.status = 'COMPLETED' " +
            "AND t.startAt >= :from AND t.startAt < :to ORDER BY t.startAt ASC")
    List<Trip> findCompletedInRange(UUID vehicleId, Instant from, Instant to);

    // NEW: backs the cost-per-km feature. distanceKm is a derived Java
    // getter (endOdometer - startOdometer), not a mapped column, so it can't
    // be SUM()'d directly in JPQL — the arithmetic is done here instead,
    // over the same two columns the getter itself subtracts. Only
    // COMPLETED trips have a non-null endOdometer, hence that filter rather
    // than relying on COALESCE to paper over nulls from ACTIVE trips.
    @Query("SELECT COALESCE(SUM(t.endOdometer - t.startOdometer), 0) FROM Trip t " +
            "WHERE t.vehicleId = :vehicleId AND t.status = 'COMPLETED'")
    int sumDistanceKmByVehicle(UUID vehicleId);
}
