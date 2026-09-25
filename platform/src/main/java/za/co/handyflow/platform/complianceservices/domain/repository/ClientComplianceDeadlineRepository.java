package za.co.handyflow.platform.complianceservices.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import za.co.handyflow.platform.complianceservices.domain.model.ClientComplianceDeadline;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ClientComplianceDeadlineRepository extends JpaRepository<ClientComplianceDeadline, UUID> {

    @Query("SELECT d FROM ClientComplianceDeadline d WHERE d.tenantId.value = :#{#tenantId.value} AND d.id = :id")
    Optional<ClientComplianceDeadline> findByIdForTenant(@Param("tenantId") TenantId tenantId, @Param("id") UUID id);

    @Query("SELECT d FROM ClientComplianceDeadline d WHERE d.tenantId.value = :#{#tenantId.value} " +
           "AND d.clientId = :clientId AND d.status = 'PENDING' ORDER BY d.dueDate ASC")
    List<ClientComplianceDeadline> findPendingByClient(@Param("tenantId") TenantId tenantId, @Param("clientId") UUID clientId);
}
