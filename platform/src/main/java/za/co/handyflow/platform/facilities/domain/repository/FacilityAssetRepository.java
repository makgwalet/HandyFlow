package za.co.handyflow.platform.facilities.domain.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import za.co.handyflow.platform.facilities.domain.model.FacilityAsset;
import za.co.handyflow.platform.shared.TenantId;

import java.util.Optional;
import java.util.UUID;

public interface FacilityAssetRepository extends JpaRepository<FacilityAsset, UUID> {

    @Query("SELECT a FROM FacilityAsset a WHERE a.tenantId = :#{#tenantId.value} AND a.id = :id AND a.deletedAt IS NULL")
    Optional<FacilityAsset> findActiveById(@Param("tenantId") TenantId tenantId, @Param("id") UUID id);

    @Query("SELECT a FROM FacilityAsset a WHERE a.tenantId = :#{#tenantId.value} AND a.deletedAt IS NULL")
    Page<FacilityAsset> findAllActive(@Param("tenantId") TenantId tenantId, Pageable pageable);

    @Query("SELECT a FROM FacilityAsset a WHERE a.tenantId = :#{#tenantId.value} AND a.siteId = :siteId AND a.deletedAt IS NULL")
    Page<FacilityAsset> findBySite(@Param("tenantId") TenantId tenantId, @Param("siteId") UUID siteId, Pageable pageable);
}
