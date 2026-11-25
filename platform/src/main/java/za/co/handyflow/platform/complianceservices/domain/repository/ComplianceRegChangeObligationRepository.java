package za.co.handyflow.platform.complianceservices.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import za.co.handyflow.platform.complianceservices.domain.model.*;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ComplianceRegChangeObligationRepository extends JpaRepository<ComplianceRegChangeObligation, UUID> {
    @Query("SELECT l FROM ComplianceRegChangeObligation l WHERE l.tenantId.value = :#{#tenantId.value} AND l.changeId = :changeId")
    List<ComplianceRegChangeObligation> findForChange(@Param("tenantId") TenantId tenantId, @Param("changeId") UUID changeId);

    @Query("SELECT l FROM ComplianceRegChangeObligation l WHERE l.tenantId.value = :#{#tenantId.value}")
    List<ComplianceRegChangeObligation> findAllForTenant(@Param("tenantId") TenantId tenantId);
}
