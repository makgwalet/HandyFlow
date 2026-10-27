package za.co.handyflow.platform.facilities.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import za.co.handyflow.platform.facilities.domain.model.FacilitySiteAccess;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.UUID;

public interface FacilitySiteAccessRepository extends JpaRepository<FacilitySiteAccess, UUID> {

    @Query("SELECT a.siteId FROM FacilitySiteAccess a WHERE a.tenantId = :#{#tenantId.value} AND a.userId = :userId")
    List<UUID> findSiteIds(@Param("tenantId") TenantId tenantId, @Param("userId") UUID userId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("DELETE FROM FacilitySiteAccess a WHERE a.tenantId = :#{#tenantId.value} AND a.userId = :userId")
    void deleteForUser(@Param("tenantId") TenantId tenantId, @Param("userId") UUID userId);
}