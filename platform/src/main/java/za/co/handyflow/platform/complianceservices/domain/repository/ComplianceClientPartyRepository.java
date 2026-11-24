package za.co.handyflow.platform.complianceservices.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import za.co.handyflow.platform.complianceservices.domain.model.ComplianceClientParty;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ComplianceClientPartyRepository extends JpaRepository<ComplianceClientParty, UUID> {

    @Query("SELECT p FROM ComplianceClientParty p WHERE p.tenantId.value = :#{#tenantId.value} AND p.clientId = :clientId ORDER BY p.role, p.fullName")
    List<ComplianceClientParty> findForClient(@Param("tenantId") TenantId tenantId, @Param("clientId") UUID clientId);

    @Query("SELECT p FROM ComplianceClientParty p WHERE p.tenantId.value = :#{#tenantId.value} AND p.clientId = :clientId AND p.id = :id")
    Optional<ComplianceClientParty> findByIdForClient(@Param("tenantId") TenantId tenantId, @Param("clientId") UUID clientId, @Param("id") UUID id);
}
