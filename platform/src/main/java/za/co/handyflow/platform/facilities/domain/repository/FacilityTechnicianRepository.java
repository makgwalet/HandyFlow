package za.co.handyflow.platform.facilities.domain.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import za.co.handyflow.platform.facilities.domain.model.FacilityTechnician;
import za.co.handyflow.platform.shared.TenantId;

import java.util.Optional;
import java.util.UUID;

public interface FacilityTechnicianRepository extends JpaRepository<FacilityTechnician, UUID> {

    @Query("SELECT t FROM FacilityTechnician t WHERE t.tenantId = :#{#tenantId.value} AND t.id = :id AND t.deletedAt IS NULL")
    Optional<FacilityTechnician> findActiveById(@Param("tenantId") TenantId tenantId, @Param("id") UUID id);

    @Query("SELECT t FROM FacilityTechnician t WHERE t.tenantId = :#{#tenantId.value} AND t.deletedAt IS NULL")
    Page<FacilityTechnician> findAllActive(@Param("tenantId") TenantId tenantId, Pageable pageable);
}
