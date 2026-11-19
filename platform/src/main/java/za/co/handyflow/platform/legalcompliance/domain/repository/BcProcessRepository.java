package za.co.handyflow.platform.legalcompliance.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.legalcompliance.domain.model.BcProcess;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BcProcessRepository extends JpaRepository<BcProcess, UUID> {

    @Query("SELECT x FROM BcProcess x WHERE x.tenantId = :tenantId AND x.id = :id")
    Optional<BcProcess> findForTenant(TenantId tenantId, UUID id);

    @Query("""
        SELECT x FROM BcProcess x WHERE x.tenantId = :tenantId
        AND (:includeRetired = TRUE OR x.status = za.co.handyflow.platform.legalcompliance.domain.model.BcProcessStatus.ACTIVE)
        ORDER BY x.name
        """)
    List<BcProcess> search(TenantId tenantId, boolean includeRetired);

    /** Cross-tenant — daily sweep: which tenants have active processes. */
    @Query("SELECT x FROM BcProcess x WHERE x.status = za.co.handyflow.platform.legalcompliance.domain.model.BcProcessStatus.ACTIVE")
    List<BcProcess> findActiveAcrossTenants();

    @Query(value = "SELECT nextval('legalcompliance_ref_seq')", nativeQuery = true)
    long nextRef();
}
