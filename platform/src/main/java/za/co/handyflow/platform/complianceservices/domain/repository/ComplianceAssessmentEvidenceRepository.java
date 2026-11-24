package za.co.handyflow.platform.complianceservices.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import za.co.handyflow.platform.complianceservices.domain.model.*;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ComplianceAssessmentEvidenceRepository extends JpaRepository<ComplianceAssessmentEvidence, UUID> {
    @Query("SELECT e FROM ComplianceAssessmentEvidence e WHERE e.tenantId.value = :#{#tenantId.value} AND e.itemId IN :itemIds")
    List<ComplianceAssessmentEvidence> findForItems(@Param("tenantId") TenantId tenantId, @Param("itemIds") List<UUID> itemIds);

    @Modifying
    @Query("DELETE FROM ComplianceAssessmentEvidence e WHERE e.itemId = :itemId")
    void deleteForItem(@Param("itemId") UUID itemId);
}
