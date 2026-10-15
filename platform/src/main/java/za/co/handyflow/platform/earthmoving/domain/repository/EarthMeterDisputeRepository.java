package za.co.handyflow.platform.earthmoving.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.earthmoving.domain.model.EarthMeterDispute;
import za.co.handyflow.platform.shared.TenantId;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EarthMeterDisputeRepository extends JpaRepository<EarthMeterDispute, UUID> {

    @Query("SELECT d FROM EarthMeterDispute d WHERE d.tenantId = :tenantId AND d.id = :id")
    Optional<EarthMeterDispute> findByTenantAndId(TenantId tenantId, UUID id);

    /** Every dispute on a machine's readings, newest first. */
    @Query("SELECT d FROM EarthMeterDispute d WHERE d.tenantId = :tenantId AND d.assetId = :assetId ORDER BY d.raisedAt DESC")
    List<EarthMeterDispute> findByAsset(TenantId tenantId, UUID assetId);

    @Query("SELECT d FROM EarthMeterDispute d WHERE d.tenantId = :tenantId AND d.assetId = :assetId AND d.status = 'OPEN'")
    List<EarthMeterDispute> findOpenByAsset(TenantId tenantId, UUID assetId);

    @Query("SELECT d FROM EarthMeterDispute d WHERE d.tenantId = :tenantId AND d.status = 'OPEN' AND d.readingId IN :readingIds")
    List<EarthMeterDispute> findOpenForReadings(TenantId tenantId, Collection<UUID> readingIds);
}
