package za.co.handyflow.platform.facilitiesmanagement.domain.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import za.co.handyflow.platform.facilitiesmanagement.domain.model.FmAsset;
import za.co.handyflow.platform.shared.TenantId;

import java.util.Optional;
import java.util.UUID;

public interface FmAssetRepository extends JpaRepository<FmAsset, UUID> {

    @Query("SELECT a FROM FmAsset a WHERE a.tenantId = :#{#tenantId.value} AND a.id = :id AND a.deletedAt IS NULL")
    Optional<FmAsset> findActiveById(@Param("tenantId") TenantId tenantId, @Param("id") UUID id);

    @Query("SELECT a FROM FmAsset a WHERE a.tenantId = :#{#tenantId.value} AND a.deletedAt IS NULL")
    Page<FmAsset> findAllActive(@Param("tenantId") TenantId tenantId, Pageable pageable);

    @Query("SELECT a FROM FmAsset a WHERE a.tenantId = :#{#tenantId.value} AND a.siteId = :siteId AND a.deletedAt IS NULL")
    Page<FmAsset> findBySite(@Param("tenantId") TenantId tenantId, @Param("siteId") UUID siteId, Pageable pageable);
}
