package za.co.handyflow.platform.legalcompliance.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.legalcompliance.domain.model.*;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CommitteeRepository extends JpaRepository<Committee, UUID> {

    @Query("SELECT x FROM Committee x WHERE x.tenantId = :tenantId AND x.id = :id")
    Optional<Committee> findForTenant(TenantId tenantId, UUID id);

    @Query("SELECT x FROM Committee x WHERE x.tenantId = :tenantId AND (:status IS NULL OR x.status = :status) ORDER BY x.name ASC")
    List<Committee> search(TenantId tenantId, CommitteeStatus status);

    /** Cross-tenant — daily sweep over committees still active. */
    @Query("SELECT x FROM Committee x WHERE x.status = za.co.handyflow.platform.legalcompliance.domain.model.CommitteeStatus.ACTIVE")
    List<Committee> findActiveAcrossTenants();

    @Query(value = "SELECT nextval('legalcompliance_ref_seq')", nativeQuery = true)
    long nextRef();
}
