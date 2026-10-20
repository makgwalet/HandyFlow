// fleet/domain/repository/TripRepository.java

package za.co.handyflow.platform.fleet.domain.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.fleet.domain.model.Trip;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.util.Collection;
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

    // ── NEW (chunk 6): driver score inputs ───────────────────────────────

    /** A driver's non-cancelled trips that started in [from, to). */
    @Query("SELECT t FROM Trip t WHERE t.tenantId = :tenantId AND t.driverId = :driverId " +
            "AND t.status <> 'CANCELLED' AND t.startAt >= :from AND t.startAt < :to ORDER BY t.startAt")
    List<Trip> findByDriverInRange(TenantId tenantId, UUID driverId, Instant from, Instant to);

    /** Every non-cancelled trip in the tenant that started in [from, to) — for the fleet-wide leaderboard. */
    @Query("SELECT t FROM Trip t WHERE t.tenantId = :tenantId AND t.status <> 'CANCELLED' " +
            "AND t.startAt >= :from AND t.startAt < :to ORDER BY t.startAt")
    List<Trip> findByTenantInRange(TenantId tenantId, Instant from, Instant to);

    /** Completed trips with fuel data on the given vehicles — the basis for each vehicle's fuel-economy norm. */
    @Query("SELECT t FROM Trip t WHERE t.tenantId = :tenantId AND t.vehicleId IN :vehicleIds " +
            "AND t.status = 'COMPLETED' AND t.fuelUsedLitres IS NOT NULL AND t.endOdometer IS NOT NULL " +
            "AND t.startAt >= :from AND t.startAt < :to")
    List<Trip> findFuelBaselineTrips(TenantId tenantId, Collection<UUID> vehicleIds, Instant from, Instant to);

    // ── NEW (chunk 7d): who was driving when a telematics event happened ───

    /** Non-cancelled trips on the vehicle that were under way at {@code at}, most recent first. Callers use the first. */
    @Query("SELECT t FROM Trip t WHERE t.vehicleId = :vehicleId AND t.status <> 'CANCELLED' " +
            "AND t.startAt <= :at AND (t.endAt IS NULL OR t.endAt >= :at) ORDER BY t.startAt DESC")
    List<Trip> findTripsAt(UUID vehicleId, Instant at);
}