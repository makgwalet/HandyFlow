package za.co.handyflow.platform.facilities.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import za.co.handyflow.platform.facilities.domain.model.FacilityStore;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FacilityStoreRepository extends JpaRepository<FacilityStore, UUID> {

    @Query("SELECT s FROM FacilityStore s WHERE s.tenantId.value = :#{#tenantId.value} AND s.id = :id")
    Optional<FacilityStore> findByIdForTenant(@Param("tenantId") TenantId tenantId, @Param("id") UUID id);

    @Query("SELECT s FROM FacilityStore s WHERE s.tenantId.value = :#{#tenantId.value} AND s.defaultStore = true")
    Optional<FacilityStore> findDefault(@Param("tenantId") TenantId tenantId);

    @Query("SELECT s FROM FacilityStore s WHERE s.tenantId.value = :#{#tenantId.value} ORDER BY s.defaultStore DESC, s.name ASC")
    List<FacilityStore> findAllForTenant(@Param("tenantId") TenantId tenantId);

    @Query("SELECT COUNT(s) > 0 FROM FacilityStore s WHERE s.tenantId.value = :#{#tenantId.value} AND s.code = :code AND s.id <> :excludeId")
    boolean codeExists(@Param("tenantId") TenantId tenantId, @Param("code") String code, @Param("excludeId") UUID excludeId);
}
