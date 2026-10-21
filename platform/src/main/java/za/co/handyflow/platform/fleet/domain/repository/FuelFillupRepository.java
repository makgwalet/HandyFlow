package za.co.handyflow.platform.fleet.domain.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.fleet.domain.model.FuelFillup;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FuelFillupRepository extends JpaRepository<FuelFillup, UUID> {

    @Query("SELECT f FROM FuelFillup f WHERE f.vehicleId = :vehicleId ORDER BY f.filledAt DESC")
    Page<FuelFillup> findByVehicle(UUID vehicleId, Pageable pageable);

    // NEW: backs the cost-per-km feature — see VehicleServiceRepository
    // .sumCostByVehicle for why COALESCE matters here too.
    @Query("SELECT COALESCE(SUM(f.totalCost), 0) FROM FuelFillup f WHERE f.vehicleId = :vehicleId")
    BigDecimal sumCostByVehicle(UUID vehicleId);

    /**
     * FIX: backlog 5.1 — lets FleetFuelDispatchEventHandler check
     * whether a fillup for this dispatch already exists before creating
     * a duplicate, as a first line of defense before the DB-level
     * UNIQUE constraint on source_fuel_dispatch_id would catch it anyway.
     */
    Optional<FuelFillup> findBySourceFuelDispatchId(UUID sourceFuelDispatchId);

    // ── NEW (chunk 7c): fuel analytics inputs ────────────────────────────

    /** One vehicle's fill-ups from {@code since}, oldest first — the order the consumption method needs. */
    @Query("SELECT f FROM FuelFillup f WHERE f.vehicleId = :vehicleId AND f.filledAt >= :since " +
            "ORDER BY f.filledAt ASC, f.odometerAtFillup ASC, f.createdAt ASC")
    List<FuelFillup> findByVehicleSinceOrdered(UUID vehicleId, LocalDate since);

    /** Every fill-up in the tenant in [from, to], oldest first. The tenant id is the raw UUID, as stored on FuelFillup. */
    @Query("SELECT f FROM FuelFillup f WHERE f.tenantId = :tenantId AND f.filledAt >= :from AND f.filledAt <= :to " +
            "ORDER BY f.filledAt ASC, f.odometerAtFillup ASC, f.createdAt ASC")
    List<FuelFillup> findByTenantInRange(UUID tenantId, LocalDate from, LocalDate to);

    /** Fill-ups in the tenant carrying any of these (upper-cased, trimmed) receipt references — for duplicate-receipt checks. */
    @Query("SELECT f FROM FuelFillup f WHERE f.tenantId = :tenantId AND f.receiptRef IS NOT NULL " +
            "AND UPPER(TRIM(f.receiptRef)) IN :receiptRefs")
    List<FuelFillup> findByTenantAndReceiptRefs(UUID tenantId, Collection<String> receiptRefs);

    /** Cross-tenant, for the daily anomaly sweep only: fill-ups recorded since {@code since}. */
    @Query("SELECT f FROM FuelFillup f WHERE f.createdAt >= :since")
    List<FuelFillup> findCreatedSince(Instant since);

    // ── Fleet-wide fuel log: every fill-up in the tenant, filtered and paged ──────────────
    // No null parameters (Postgres cannot type them): "all vehicles" is a flag, and an empty search is the pattern '%'.

    @Query("SELECT f FROM FuelFillup f WHERE f.tenantId = :tenantUuid AND f.filledAt >= :from AND f.filledAt <= :to " +
            "AND (:allVehicles = true OR f.vehicleId = :vehicleId) " +
            "AND (LOWER(COALESCE(f.station, '')) LIKE :pattern OR LOWER(COALESCE(f.receiptRef, '')) LIKE :pattern " +
            "OR f.vehicleId IN (SELECT v.id FROM Vehicle v WHERE v.tenantId = :tenantId AND LOWER(v.registration) LIKE :pattern)) " +
            "ORDER BY f.filledAt DESC, f.createdAt DESC")
    Page<FuelFillup> searchLog(UUID tenantUuid, TenantId tenantId, LocalDate from, LocalDate to,
                               boolean allVehicles, UUID vehicleId, String pattern, Pageable pageable);

    /** One row of [count, litres, spend] over the same filter as {@link #searchLog}. */
    @Query("SELECT COUNT(f), COALESCE(SUM(f.litres), 0), COALESCE(SUM(f.totalCost), 0) FROM FuelFillup f " +
            "WHERE f.tenantId = :tenantUuid AND f.filledAt >= :from AND f.filledAt <= :to " +
            "AND (:allVehicles = true OR f.vehicleId = :vehicleId) " +
            "AND (LOWER(COALESCE(f.station, '')) LIKE :pattern OR LOWER(COALESCE(f.receiptRef, '')) LIKE :pattern " +
            "OR f.vehicleId IN (SELECT v.id FROM Vehicle v WHERE v.tenantId = :tenantId AND LOWER(v.registration) LIKE :pattern))")
    List<Object[]> totalsFor(UUID tenantUuid, TenantId tenantId, LocalDate from, LocalDate to,
                             boolean allVehicles, UUID vehicleId, String pattern);
}