package za.co.handyflow.platform.facilitiesmanagement.domain.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import za.co.handyflow.platform.facilitiesmanagement.domain.model.FmSite;
import za.co.handyflow.platform.shared.TenantId;

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
}
