package za.co.handyflow.platform.facilities.domain.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import za.co.handyflow.platform.facilities.domain.model.FacilityAsset;
import za.co.handyflow.platform.shared.TenantId;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

public interface FacilityAssetRepository extends JpaRepository<FacilityAsset, UUID> {

    @Query("SELECT a FROM FacilityAsset a WHERE a.tenantId.value = :#{#tenantId.value} AND a.id = :id AND a.deletedAt IS NULL")
    Optional<FacilityAsset> findActiveById(@Param("tenantId") TenantId tenantId, @Param("id") UUID id);

    @Query("SELECT a FROM FacilityAsset a WHERE a.tenantId.value = :#{#tenantId.value} AND a.deletedAt IS NULL")
    Page<FacilityAsset> findAllActive(@Param("tenantId") TenantId tenantId, Pageable pageable);

    @Query("SELECT a FROM FacilityAsset a WHERE a.tenantId.value = :#{#tenantId.value} AND a.siteId = :siteId AND a.deletedAt IS NULL")
    Page<FacilityAsset> findBySite(@Param("tenantId") TenantId tenantId, @Param("siteId") UUID siteId, Pageable pageable);

    /** Scope-aware list across sites. {@code siteIds} must never be empty. */
    @Query("SELECT a FROM FacilityAsset a WHERE a.tenantId.value = :#{#tenantId.value} AND a.deletedAt IS NULL " +
            "AND (:allSites = true OR a.siteId IN :siteIds)")
    Page<FacilityAsset> findAllActiveInScope(@Param("tenantId") TenantId tenantId, @Param("allSites") boolean allSites,
                                             @Param("siteIds") Collection<UUID> siteIds, Pageable pageable);

    /** Exact asset-tag match (tags are unique per tenant among live assets). */
    @Query("SELECT a FROM FacilityAsset a WHERE a.tenantId.value = :#{#tenantId.value} AND a.assetTag = :tag AND a.deletedAt IS NULL")
    Optional<FacilityAsset> findActiveByTag(@Param("tenantId") TenantId tenantId, @Param("tag") String tag);
}
