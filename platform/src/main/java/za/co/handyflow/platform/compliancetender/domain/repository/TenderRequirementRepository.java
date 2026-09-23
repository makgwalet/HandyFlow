package za.co.handyflow.platform.compliancetender.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import za.co.handyflow.platform.compliancetender.domain.model.TenderRequirement;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TenderRequirementRepository extends JpaRepository<TenderRequirement, UUID> {

    @Query("SELECT r FROM TenderRequirement r WHERE r.tenantId = :#{#tenantId.value} AND r.id = :id")
    Optional<TenderRequirement> findByIdForTenant(@Param("tenantId") TenantId tenantId, @Param("id") UUID id);

    @Query("SELECT r FROM TenderRequirement r WHERE r.tenantId = :#{#tenantId.value} " +
           "AND r.tenderId = :tenderId ORDER BY r.source, r.description")
    List<TenderRequirement> findByTender(@Param("tenantId") TenantId tenantId, @Param("tenderId") UUID tenderId);
}
