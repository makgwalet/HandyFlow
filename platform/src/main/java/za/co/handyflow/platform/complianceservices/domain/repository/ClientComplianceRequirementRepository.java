package za.co.handyflow.platform.complianceservices.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import za.co.handyflow.platform.complianceservices.domain.model.ClientComplianceRequirement;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ClientComplianceRequirementRepository extends JpaRepository<ClientComplianceRequirement, UUID> {

    @Query("SELECT r FROM ClientComplianceRequirement r WHERE r.tenantId = :#{#tenantId.value} AND r.id = :id")
    Optional<ClientComplianceRequirement> findByIdForTenant(@Param("tenantId") TenantId tenantId, @Param("id") UUID id);

    @Query("SELECT r FROM ClientComplianceRequirement r WHERE r.tenantId = :#{#tenantId.value} " +
           "AND r.clientId = :clientId ORDER BY r.code")
    List<ClientComplianceRequirement> findAllForClient(@Param("tenantId") TenantId tenantId, @Param("clientId") UUID clientId);

    @Query("SELECT r FROM ClientComplianceRequirement r WHERE r.tenantId = :#{#tenantId.value} " +
           "AND r.clientId = :clientId AND r.code = :code ORDER BY r.requirementVersion DESC LIMIT 1")
    Optional<ClientComplianceRequirement> findLatestByCode(@Param("tenantId") TenantId tenantId,
                                                           @Param("clientId") UUID clientId, @Param("code") String code);
}
