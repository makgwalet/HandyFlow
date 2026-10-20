package za.co.handyflow.platform.fleet.domain.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.fleet.domain.model.AssetClass;
import za.co.handyflow.platform.fleet.domain.model.AssetProfile;
import za.co.handyflow.platform.shared.TenantId;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AssetProfileRepository extends JpaRepository<AssetProfile, UUID> {

    Optional<AssetProfile> findByTenantIdAndVehicleId(TenantId tenantId, UUID vehicleId);

    List<AssetProfile> findByTenantIdAndVehicleIdIn(TenantId tenantId, Collection<UUID> vehicleIds);

    /** Trailers currently coupled to this vehicle. */
    @Query("SELECT p FROM AssetProfile p WHERE p.tenantId = :tenantId AND p.coupledToVehicleId = :hostId")
    List<AssetProfile> findCoupledTo(TenantId tenantId, UUID hostId);

    /** Profiles of the given classes — used for the fleet-wide utilisation board. */
    @Query("SELECT p FROM AssetProfile p WHERE p.tenantId = :tenantId AND p.assetClass IN :classes")
    List<AssetProfile> findByClasses(TenantId tenantId, Collection<AssetClass> classes);

    /**
     * Every live vehicle with its profile (null when it has none, i.e. a plain road vehicle), newest
     * registration order. Rows are {@code Object[]{Vehicle, AssetProfile-or-null}}.
     */
    @Query(value = "SELECT v, p FROM Vehicle v LEFT JOIN AssetProfile p ON p.vehicleId = v.id " +
            "WHERE v.tenantId = :tenantId AND v.deletedAt IS NULL ORDER BY v.registration",
            countQuery = "SELECT COUNT(v) FROM Vehicle v WHERE v.tenantId = :tenantId AND v.deletedAt IS NULL")
    Page<Object[]> findAllAssets(TenantId tenantId, Pageable pageable);

    /** As {@link #findAllAssets}, limited to one class. A vehicle with no profile counts as ROAD_VEHICLE. */
    @Query(value = "SELECT v, p FROM Vehicle v LEFT JOIN AssetProfile p ON p.vehicleId = v.id " +
            "WHERE v.tenantId = :tenantId AND v.deletedAt IS NULL " +
            "AND COALESCE(p.assetClass, za.co.handyflow.platform.fleet.domain.model.AssetClass.ROAD_VEHICLE) = :assetClass " +
            "ORDER BY v.registration",
            countQuery = "SELECT COUNT(v) FROM Vehicle v LEFT JOIN AssetProfile p ON p.vehicleId = v.id " +
                    "WHERE v.tenantId = :tenantId AND v.deletedAt IS NULL " +
                    "AND COALESCE(p.assetClass, za.co.handyflow.platform.fleet.domain.model.AssetClass.ROAD_VEHICLE) = :assetClass")
    Page<Object[]> findAssetsByClass(TenantId tenantId, AssetClass assetClass, Pageable pageable);
}