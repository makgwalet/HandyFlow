package za.co.handyflow.platform.complianceservices.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import za.co.handyflow.platform.complianceservices.domain.model.ComplianceProviderDeliverable;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ComplianceProviderDeliverableRepository extends JpaRepository<ComplianceProviderDeliverable, UUID> {

    @Query("SELECT d FROM ComplianceProviderDeliverable d WHERE d.tenantId.value = :#{#tenantId.value} AND d.id = :id")
    Optional<ComplianceProviderDeliverable> findOne(@Param("tenantId") TenantId tenantId, @Param("id") UUID id);

    @Query("SELECT d FROM ComplianceProviderDeliverable d WHERE d.tenantId.value = :#{#tenantId.value} AND d.assignmentId = :assignmentId ORDER BY d.createdAt")
    List<ComplianceProviderDeliverable> findForAssignment(@Param("tenantId") TenantId tenantId, @Param("assignmentId") UUID assignmentId);

    @Query("SELECT d FROM ComplianceProviderDeliverable d WHERE d.tenantId.value = :#{#tenantId.value} AND d.status = 'RECEIVED'")
    List<ComplianceProviderDeliverable> findAwaitingReview(@Param("tenantId") TenantId tenantId);
}
