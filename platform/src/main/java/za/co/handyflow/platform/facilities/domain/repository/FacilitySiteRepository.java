package za.co.handyflow.platform.facilities.domain.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import za.co.handyflow.platform.facilities.domain.model.FacilitySite;
import za.co.handyflow.platform.shared.TenantId;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

public interface FacilitySiteRepository extends JpaRepository<FacilitySite, UUID> {

    @Query("SELECT s FROM FacilitySite s WHERE s.tenantId.value = :#{#tenantId.value} AND s.id = :id AND s.deletedAt IS NULL")
    Optional<FacilitySite> findActiveById(@Param("tenantId") TenantId tenantId, @Param("id") UUID id);

    @Query("SELECT s FROM FacilitySite s WHERE s.tenantId.value = :#{#tenantId.value} AND s.deletedAt IS NULL")
    Page<FacilitySite> findAllActive(@Param("tenantId") TenantId tenantId, Pageable pageable);

    /** Scope-aware list. {@code siteIds} must never be empty (the access policy guarantees a placeholder). */
    @Query("SELECT s FROM FacilitySite s WHERE s.tenantId.value = :#{#tenantId.value} AND s.deletedAt IS NULL " +
            "AND (:allSites = true OR s.id IN :siteIds)")
    Page<FacilitySite> findAllActiveInScope(@Param("tenantId") TenantId tenantId, @Param("allSites") boolean allSites,
                                            @Param("siteIds") Collection<UUID> siteIds, Pageable pageable);
}