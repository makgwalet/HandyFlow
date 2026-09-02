package za.co.handyflow.platform.facilitiesmanagement.domain.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import za.co.handyflow.platform.facilitiesmanagement.domain.model.FmTechnician;
import za.co.handyflow.platform.shared.TenantId;

import java.util.Optional;
import java.util.UUID;

public interface FmTechnicianRepository extends JpaRepository<FmTechnician, UUID> {

    @Query("SELECT t FROM FmTechnician t WHERE t.tenantId = :#{#tenantId.value} AND t.id = :id AND t.deletedAt IS NULL")
    Optional<FmTechnician> findActiveById(@Param("tenantId") TenantId tenantId, @Param("id") UUID id);

    @Query("SELECT t FROM FmTechnician t WHERE t.tenantId = :#{#tenantId.value} AND t.deletedAt IS NULL")
    Page<FmTechnician> findAllActive(@Param("tenantId") TenantId tenantId, Pageable pageable);
}
