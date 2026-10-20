package za.co.handyflow.platform.fleet.domain.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.fleet.domain.model.Tyre;
import za.co.handyflow.platform.fleet.domain.model.TyreStatus;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TyreRepository extends JpaRepository<Tyre, UUID> {

    @Query("SELECT t FROM Tyre t WHERE t.tenantId = :tenantId AND t.id = :id")
    Optional<Tyre> findByTenantAndId(TenantId tenantId, UUID id);

    @Query("SELECT t FROM Tyre t WHERE t.tenantId = :tenantId ORDER BY t.serialNumber")
    Page<Tyre> findAllForTenant(TenantId tenantId, Pageable pageable);

    @Query("SELECT t FROM Tyre t WHERE t.tenantId = :tenantId AND t.status = :status ORDER BY t.serialNumber")
    Page<Tyre> findByStatus(TenantId tenantId, TyreStatus status, Pageable pageable);

    /** Tyres currently on this vehicle, in position order (spares last). */
    @Query("SELECT t FROM Tyre t WHERE t.tenantId = :tenantId AND t.currentVehicleId = :vehicleId " +
            "AND t.status = za.co.handyflow.platform.fleet.domain.model.TyreStatus.FITTED ORDER BY t.currentPosition")
    List<Tyre> findFittedByVehicle(TenantId tenantId, UUID vehicleId);

    @Query("SELECT t FROM Tyre t WHERE t.tenantId = :tenantId AND t.currentVehicleId = :vehicleId " +
            "AND t.currentPosition = :position AND t.status = za.co.handyflow.platform.fleet.domain.model.TyreStatus.FITTED")
    Optional<Tyre> findFittedAt(TenantId tenantId, UUID vehicleId, String position);

    @Query("SELECT COUNT(t) > 0 FROM Tyre t WHERE t.tenantId = :tenantId AND LOWER(t.serialNumber) = LOWER(:serial) " +
            "AND t.id <> :excludeId")
    boolean existsOtherWithSerial(TenantId tenantId, String serial, UUID excludeId);
}