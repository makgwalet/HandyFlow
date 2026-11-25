package za.co.handyflow.platform.trainingprovider.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.shared.TenantId;
import za.co.handyflow.platform.trainingprovider.domain.model.TrainProvAuthority;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TrainProvAuthorityRepository extends JpaRepository<TrainProvAuthority, UUID> {

    @Query("SELECT a FROM TrainProvAuthority a WHERE a.tenantId = :#{#tenantId.value} ORDER BY a.status, a.name")
    List<TrainProvAuthority> findAllForTenant(TenantId tenantId);

    @Query("SELECT a FROM TrainProvAuthority a WHERE a.tenantId = :#{#tenantId.value} AND a.id = :id")
    Optional<TrainProvAuthority> findForTenant(TenantId tenantId, UUID id);

    @Query("SELECT COUNT(a) > 0 FROM TrainProvAuthority a WHERE a.tenantId = :#{#tenantId.value} AND LOWER(a.name) = LOWER(:name) AND (:excludeId IS NULL OR a.id <> :excludeId)")
    boolean nameTaken(TenantId tenantId, String name, UUID excludeId);
}
