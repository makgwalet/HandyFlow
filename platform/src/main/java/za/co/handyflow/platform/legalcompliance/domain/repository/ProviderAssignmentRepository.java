package za.co.handyflow.platform.legalcompliance.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.legalcompliance.domain.model.ProviderAssignment;
import za.co.handyflow.platform.shared.TenantId;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProviderAssignmentRepository extends JpaRepository<ProviderAssignment, UUID> {

    @Query("SELECT x FROM ProviderAssignment x WHERE x.tenantId = :tenantId AND x.providerClientId = :clientId ORDER BY x.endedOn NULLS FIRST, x.assignedOn DESC")
    List<ProviderAssignment> findForClient(TenantId tenantId, UUID clientId);

    @Query("SELECT x FROM ProviderAssignment x WHERE x.tenantId = :tenantId AND x.providerClientId IN :clientIds AND x.endedOn IS NULL")
    List<ProviderAssignment> findActiveForClients(TenantId tenantId, Collection<UUID> clientIds);

    @Query("SELECT x FROM ProviderAssignment x WHERE x.tenantId = :tenantId AND x.endedOn IS NULL")
    List<ProviderAssignment> findAllActive(TenantId tenantId);

    @Query("SELECT x FROM ProviderAssignment x WHERE x.tenantId = :tenantId AND x.userId = :userId AND x.endedOn IS NULL")
    List<ProviderAssignment> findActiveForUser(TenantId tenantId, UUID userId);

    @Query("SELECT x FROM ProviderAssignment x WHERE x.tenantId = :tenantId AND x.providerClientId = :clientId AND x.userId = :userId AND x.endedOn IS NULL")
    Optional<ProviderAssignment> findActive(TenantId tenantId, UUID clientId, UUID userId);

    @Query("SELECT x FROM ProviderAssignment x WHERE x.tenantId = :tenantId AND x.id = :id")
    Optional<ProviderAssignment> findForTenant(TenantId tenantId, UUID id);
}
