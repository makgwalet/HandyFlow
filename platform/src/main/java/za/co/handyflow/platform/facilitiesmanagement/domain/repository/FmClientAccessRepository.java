package za.co.handyflow.platform.facilitiesmanagement.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import za.co.handyflow.platform.facilitiesmanagement.domain.model.FmClientAccess;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.UUID;

public interface FmClientAccessRepository extends JpaRepository<FmClientAccess, UUID> {

    @Query("SELECT a.clientId FROM FmClientAccess a WHERE a.tenantId = :#{#tenantId.value} AND a.userId = :userId")
    List<UUID> findClientIds(@Param("tenantId") TenantId tenantId, @Param("userId") UUID userId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("DELETE FROM FmClientAccess a WHERE a.tenantId = :#{#tenantId.value} AND a.userId = :userId")
    void deleteForUser(@Param("tenantId") TenantId tenantId, @Param("userId") UUID userId);
}