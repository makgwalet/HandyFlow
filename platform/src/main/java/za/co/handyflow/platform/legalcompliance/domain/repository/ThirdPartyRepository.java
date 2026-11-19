package za.co.handyflow.platform.legalcompliance.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.legalcompliance.domain.model.*;
import za.co.handyflow.platform.shared.TenantId;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ThirdPartyRepository extends JpaRepository<ThirdParty, UUID> {

    @Query("SELECT x FROM ThirdParty x WHERE x.tenantId = :tenantId AND x.id = :id")
    Optional<ThirdParty> findForTenant(TenantId tenantId, UUID id);

    @Query("SELECT x FROM ThirdParty x WHERE x.tenantId = :tenantId AND (:status IS NULL OR x.status = :status) ORDER BY x.name")
    List<ThirdParty> search(TenantId tenantId, ThirdPartyStatus status);

    /** Cross-tenant — daily sweep: approved parties whose periodic review is due. */
    @Query("SELECT x FROM ThirdParty x WHERE x.status = za.co.handyflow.platform.legalcompliance.domain.model.ThirdPartyStatus.APPROVED AND x.nextReviewDue IS NOT NULL AND x.nextReviewDue <= :horizon")
    List<ThirdParty> findReviewDueAcrossTenants(LocalDate horizon);

    @Query(value = "SELECT nextval('legalcompliance_ref_seq')", nativeQuery = true)
    long nextRef();
}
