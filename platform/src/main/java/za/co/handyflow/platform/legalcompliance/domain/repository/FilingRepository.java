package za.co.handyflow.platform.legalcompliance.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.legalcompliance.domain.model.*;
import za.co.handyflow.platform.shared.TenantId;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FilingRepository extends JpaRepository<Filing, UUID> {

    @Query("SELECT x FROM Filing x WHERE x.tenantId = :tenantId AND x.id = :id")
    Optional<Filing> findForTenant(TenantId tenantId, UUID id);

    @Query("""
        SELECT x FROM Filing x WHERE x.tenantId = :tenantId
        AND (:includeDone = TRUE OR x.status IN (za.co.handyflow.platform.legalcompliance.domain.model.FilingStatus.PLANNED, za.co.handyflow.platform.legalcompliance.domain.model.FilingStatus.PREPARED))
        ORDER BY x.dueDate ASC
        """)
    List<Filing> search(TenantId tenantId, boolean includeDone);

    /** Cross-tenant — daily sweep: filings not yet submitted. */
    @Query("SELECT x FROM Filing x WHERE x.status IN (za.co.handyflow.platform.legalcompliance.domain.model.FilingStatus.PLANNED, za.co.handyflow.platform.legalcompliance.domain.model.FilingStatus.PREPARED) AND x.dueDate <= :horizon")
    List<Filing> findPendingAcrossTenants(LocalDate horizon);

    @Query(value = "SELECT nextval('legalcompliance_ref_seq')", nativeQuery = true)
    long nextRef();
}
