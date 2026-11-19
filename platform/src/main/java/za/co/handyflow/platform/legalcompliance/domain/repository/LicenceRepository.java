package za.co.handyflow.platform.legalcompliance.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.legalcompliance.domain.model.*;
import za.co.handyflow.platform.shared.TenantId;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LicenceRepository extends JpaRepository<Licence, UUID> {

    @Query("SELECT x FROM Licence x WHERE x.tenantId = :tenantId AND x.id = :id")
    Optional<Licence> findForTenant(TenantId tenantId, UUID id);

    @Query("""
        SELECT x FROM Licence x WHERE x.tenantId = :tenantId
        AND (:status IS NULL OR x.status = :status)
        AND (:includeEnded = TRUE OR x.status NOT IN (za.co.handyflow.platform.legalcompliance.domain.model.LicenceStatus.REVOKED, za.co.handyflow.platform.legalcompliance.domain.model.LicenceStatus.SURRENDERED))
        ORDER BY CASE WHEN x.expiryDate IS NULL THEN 1 ELSE 0 END, x.expiryDate ASC
        """)
    List<Licence> search(TenantId tenantId, LicenceStatus status, boolean includeEnded);

    /** Cross-tenant — daily sweep: licences still in force whose expiry is on or before the horizon (including those already expired). */
    @Query("SELECT x FROM Licence x WHERE x.status IN (za.co.handyflow.platform.legalcompliance.domain.model.LicenceStatus.ACTIVE, za.co.handyflow.platform.legalcompliance.domain.model.LicenceStatus.RENEWAL_IN_PROGRESS) AND x.expiryDate IS NOT NULL AND x.expiryDate <= :horizon")
    List<Licence> findExpiringAcrossTenants(LocalDate horizon);

    @Query(value = "SELECT nextval('legalcompliance_ref_seq')", nativeQuery = true)
    long nextRef();
}
