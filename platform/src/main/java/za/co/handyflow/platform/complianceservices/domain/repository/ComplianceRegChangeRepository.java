package za.co.handyflow.platform.complianceservices.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import za.co.handyflow.platform.complianceservices.domain.model.*;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ComplianceRegChangeRepository extends JpaRepository<ComplianceRegChange, UUID> {
    @Query("SELECT c FROM ComplianceRegChange c WHERE c.tenantId.value = :#{#tenantId.value} ORDER BY c.raisedAt DESC")
    List<ComplianceRegChange> findAllForTenant(@Param("tenantId") TenantId tenantId);

    @Query("SELECT c FROM ComplianceRegChange c WHERE c.tenantId.value = :#{#tenantId.value} AND c.status = :status ORDER BY c.raisedAt DESC")
    List<ComplianceRegChange> findByStatus(@Param("tenantId") TenantId tenantId, @Param("status") String status);

    @Query("SELECT c FROM ComplianceRegChange c WHERE c.tenantId.value = :#{#tenantId.value} AND c.id = :id")
    Optional<ComplianceRegChange> findOne(@Param("tenantId") TenantId tenantId, @Param("id") UUID id);

    @Query("SELECT c FROM ComplianceRegChange c WHERE c.tenantId.value = :#{#tenantId.value} AND c.status IN ('DETECTED','IN_REVIEW')")
    List<ComplianceRegChange> findAwaitingReview(@Param("tenantId") TenantId tenantId);

    @Query("SELECT c FROM ComplianceRegChange c WHERE c.tenantId.value = :#{#tenantId.value} AND c.id IN :ids")
    List<ComplianceRegChange> findByIds(@Param("tenantId") TenantId tenantId, @Param("ids") java.util.Collection<UUID> ids);
}
