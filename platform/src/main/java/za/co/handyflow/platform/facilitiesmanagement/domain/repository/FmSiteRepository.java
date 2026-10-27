package za.co.handyflow.platform.facilitiesmanagement.domain.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import za.co.handyflow.platform.facilitiesmanagement.domain.model.FmSite;
import za.co.handyflow.platform.shared.TenantId;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FmSiteRepository extends JpaRepository<FmSite, UUID> {

    @Query("SELECT s FROM FmSite s WHERE s.tenantId = :#{#tenantId.value} AND s.id = :id AND s.deletedAt IS NULL")
    Optional<FmSite> findActiveById(@Param("tenantId") TenantId tenantId, @Param("id") UUID id);

    @Query("SELECT s FROM FmSite s WHERE s.tenantId = :#{#tenantId.value} AND s.clientId = :clientId AND s.id = :id AND s.deletedAt IS NULL")
    Optional<FmSite> findActiveByIdForClient(@Param("tenantId") TenantId tenantId, @Param("clientId") UUID clientId, @Param("id") UUID id);

    @Query("SELECT s FROM FmSite s WHERE s.tenantId = :#{#tenantId.value} AND s.deletedAt IS NULL")
    Page<FmSite> findAllActive(@Param("tenantId") TenantId tenantId, Pageable pageable);

    @Query("SELECT s FROM FmSite s WHERE s.tenantId = :#{#tenantId.value} AND s.clientId = :clientId AND s.deletedAt IS NULL")
    Page<FmSite> findAllActiveForClient(@Param("tenantId") TenantId tenantId, @Param("clientId") UUID clientId, Pageable pageable);

    /** Scope-aware list across clients. {@code siteIds} must never be empty. */
    @Query("SELECT s FROM FmSite s WHERE s.tenantId = :#{#tenantId.value} AND s.deletedAt IS NULL " +
            "AND (:allSites = true OR s.id IN :siteIds)")
    Page<FmSite> findAllActiveInScope(@Param("tenantId") TenantId tenantId, @Param("allSites") boolean allSites,
                                      @Param("siteIds") Collection<UUID> siteIds, Pageable pageable);

    /** Ids of every non-deleted site belonging to any of the given clients — resolves a client-restricted user's site scope. */
    @Query("SELECT s.id FROM FmSite s WHERE s.tenantId = :#{#tenantId.value} AND s.clientId IN :clientIds AND s.deletedAt IS NULL")
    List<UUID> findSiteIdsForClients(@Param("tenantId") TenantId tenantId, @Param("clientIds") Collection<UUID> clientIds);
}