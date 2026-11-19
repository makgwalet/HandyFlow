package za.co.handyflow.platform.legalcompliance.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.legalcompliance.domain.model.*;
import za.co.handyflow.platform.shared.TenantId;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FicaPartyRepository extends JpaRepository<FicaParty, UUID> {

    @Query("SELECT x FROM FicaParty x WHERE x.tenantId = :tenantId AND x.id = :id")
    Optional<FicaParty> findForTenant(TenantId tenantId, UUID id);

    @Query("SELECT x FROM FicaParty x WHERE x.tenantId = :tenantId AND (:status IS NULL OR x.status = :status) ORDER BY x.createdAt DESC")
    List<FicaParty> search(TenantId tenantId, FicaPartyStatus status);

    /** Cross-tenant — daily sweep: verified parties whose periodic review is due. */
    @Query("SELECT x FROM FicaParty x WHERE x.status = za.co.handyflow.platform.legalcompliance.domain.model.FicaPartyStatus.VERIFIED AND x.nextReviewDue IS NOT NULL AND x.nextReviewDue <= :horizon")
    List<FicaParty> findReviewDueAcrossTenants(LocalDate horizon);

    @Query(value = "SELECT nextval('legalcompliance_ref_seq')", nativeQuery = true)
    long nextRef();
}
