package za.co.handyflow.platform.facilities.domain.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import za.co.handyflow.platform.facilities.domain.model.FacilitySite;
import za.co.handyflow.platform.shared.TenantId;

import java.util.Optional;
import java.util.UUID;

public interface FacilitySiteRepository extends JpaRepository<FacilitySite, UUID> {

    @Query("SELECT s FROM FacilitySite s WHERE s.tenantId = :#{#tenantId.value} AND s.id = :id AND s.deletedAt IS NULL")
    Optional<FacilitySite> findActiveById(@Param("tenantId") TenantId tenantId, @Param("id") UUID id);

    @Query("SELECT s FROM FacilitySite s WHERE s.tenantId = :#{#tenantId.value} AND s.deletedAt IS NULL")
    Page<FacilitySite> findAllActive(@Param("tenantId") TenantId tenantId, Pageable pageable);
}
