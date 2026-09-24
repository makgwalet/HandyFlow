package za.co.handyflow.platform.complianceservices.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import za.co.handyflow.platform.complianceservices.domain.model.ClientTenderPersonnel;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ClientTenderPersonnelRepository extends JpaRepository<ClientTenderPersonnel, UUID> {

    @Query("SELECT p FROM ClientTenderPersonnel p WHERE p.tenantId = :#{#tenantId.value} AND p.id = :id")
    Optional<ClientTenderPersonnel> findByIdForTenant(@Param("tenantId") TenantId tenantId, @Param("id") UUID id);

    @Query("SELECT p FROM ClientTenderPersonnel p WHERE p.tenantId = :#{#tenantId.value} " +
           "AND p.clientTenderId = :clientTenderId ORDER BY p.createdAt")
    List<ClientTenderPersonnel> findByTender(@Param("tenantId") TenantId tenantId, @Param("clientTenderId") UUID clientTenderId);
}
