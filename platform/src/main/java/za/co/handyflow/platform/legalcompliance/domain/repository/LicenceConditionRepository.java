package za.co.handyflow.platform.legalcompliance.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.legalcompliance.domain.model.*;
import za.co.handyflow.platform.shared.TenantId;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LicenceConditionRepository extends JpaRepository<LicenceCondition, UUID> {

    @Query("SELECT x FROM LicenceCondition x WHERE x.tenantId = :tenantId AND x.id = :id")
    Optional<LicenceCondition> findForTenant(TenantId tenantId, UUID id);

    @Query("SELECT x FROM LicenceCondition x WHERE x.tenantId = :tenantId AND x.licenceId = :licenceId ORDER BY x.dueDate ASC, x.createdAt ASC")
    List<LicenceCondition> findForLicence(TenantId tenantId, UUID licenceId);

    @Query("SELECT x FROM LicenceCondition x WHERE x.tenantId = :tenantId AND x.licenceId IN :ids")
    List<LicenceCondition> findForLicences(TenantId tenantId, Collection<UUID> ids);

    /** Cross-tenant — daily sweep: pending conditions due on or before the horizon, on licences still in force. */
    @Query("""
        SELECT c FROM LicenceCondition c, Licence l WHERE c.licenceId = l.id
        AND l.status NOT IN (za.co.handyflow.platform.legalcompliance.domain.model.LicenceStatus.REVOKED, za.co.handyflow.platform.legalcompliance.domain.model.LicenceStatus.SURRENDERED)
        AND c.status = za.co.handyflow.platform.legalcompliance.domain.model.ConditionStatus.PENDING AND c.dueDate IS NOT NULL AND c.dueDate <= :horizon
        """)
    List<LicenceCondition> findDueAcrossTenants(LocalDate horizon);
}
