package za.co.handyflow.platform.fuel.domain.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.fuel.domain.model.*;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FuelReservationRepository extends JpaRepository<FuelReservation, UUID> {

    @Query("SELECT r FROM FuelReservation r WHERE r.tenantId = :tenantId AND r.id = :id")
    Optional<FuelReservation> findOwned(TenantId tenantId, UUID id);

    @Query("SELECT r FROM FuelReservation r WHERE r.tenantId = :tenantId ORDER BY r.createdAt DESC")
    Page<FuelReservation> findAllForTenant(TenantId tenantId, Pageable pageable);

    @Query("SELECT r FROM FuelReservation r WHERE r.tenantId = :tenantId AND r.status = 'ACTIVE' AND (r.expiresAt IS NULL OR r.expiresAt > :now) ORDER BY r.createdAt DESC")
    List<FuelReservation> findLive(TenantId tenantId, Instant now);

    /** Litres held by live reservations on one tank. Expired and closed ones do not count. */
    @Query("SELECT COALESCE(SUM(r.litres), 0) FROM FuelReservation r WHERE r.tenantId = :tenantId AND r.tankId = :tankId AND r.status = 'ACTIVE' AND (r.expiresAt IS NULL OR r.expiresAt > :now)")
    BigDecimal sumLiveForTank(TenantId tenantId, UUID tankId, Instant now);

    @Query("SELECT r FROM FuelReservation r WHERE r.tenantId = :tenantId AND r.tankId = :tankId AND r.referenceType = :refType AND r.referenceId = :refId AND r.status = 'ACTIVE'")
    List<FuelReservation> findActiveByReference(TenantId tenantId, UUID tankId, String refType, UUID refId);

    @Query("SELECT r.tankId, COALESCE(SUM(r.litres), 0) FROM FuelReservation r WHERE r.tenantId = :tenantId AND r.status = 'ACTIVE' AND (r.expiresAt IS NULL OR r.expiresAt > :now) GROUP BY r.tankId")
    List<Object[]> sumLiveByTank(TenantId tenantId, Instant now);
}
