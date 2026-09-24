package za.co.handyflow.platform.complianceservices.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import za.co.handyflow.platform.complianceservices.domain.model.ClientTenderRequirement;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ClientTenderRequirementRepository extends JpaRepository<ClientTenderRequirement, UUID> {

    @Query("SELECT r FROM ClientTenderRequirement r WHERE r.tenantId = :#{#tenantId.value} AND r.id = :id")
    Optional<ClientTenderRequirement> findByIdForTenant(@Param("tenantId") TenantId tenantId, @Param("id") UUID id);

    @Query("SELECT r FROM ClientTenderRequirement r WHERE r.tenantId = :#{#tenantId.value} " +
           "AND r.clientTenderId = :clientTenderId ORDER BY r.source, r.description")
    List<ClientTenderRequirement> findByTender(@Param("tenantId") TenantId tenantId, @Param("clientTenderId") UUID clientTenderId);
}
