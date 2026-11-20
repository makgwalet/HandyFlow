package za.co.handyflow.platform.legalcompliance.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.legalcompliance.domain.model.WrittenResponse;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.UUID;

public interface WrittenResponseRepository extends JpaRepository<WrittenResponse, UUID> {

    @Query("SELECT x FROM WrittenResponse x WHERE x.tenantId = :tenantId AND x.resolutionId = :resolutionId ORDER BY x.memberName ASC")
    List<WrittenResponse> findForResolution(TenantId tenantId, UUID resolutionId);

    @Query("SELECT x FROM WrittenResponse x WHERE x.tenantId = :tenantId AND x.userId = :userId ORDER BY x.createdAt DESC")
    List<WrittenResponse> findForUser(TenantId tenantId, UUID userId);
}
