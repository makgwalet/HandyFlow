package za.co.handyflow.platform.legalcompliance.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.legalcompliance.domain.model.*;
import za.co.handyflow.platform.shared.TenantId;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PostureSnapshotRepository extends JpaRepository<PostureSnapshot, UUID> {

    @Query("SELECT x FROM PostureSnapshot x WHERE x.tenantId = :tenantId AND x.id = :id")
    Optional<PostureSnapshot> findForTenant(TenantId tenantId, UUID id);

    @Query("SELECT x FROM PostureSnapshot x WHERE x.tenantId = :tenantId AND x.snapshotDate = :date")
    Optional<PostureSnapshot> findByDate(TenantId tenantId, LocalDate date);

    @Query("SELECT x FROM PostureSnapshot x WHERE x.tenantId = :tenantId AND x.snapshotDate >= :since ORDER BY x.snapshotDate ASC")
    List<PostureSnapshot> history(TenantId tenantId, LocalDate since);

    /** Cross-tenant — monthly job: tenants that use the module (they have obligations) or have been snapshotted before. */
    @Query("SELECT DISTINCT o.tenantId FROM RegulatoryObligation o WHERE o.deletedAt IS NULL")
    List<TenantId> tenantsWithObligations();

    @Query("SELECT DISTINCT x.tenantId FROM PostureSnapshot x")
    List<TenantId> tenantsWithSnapshots();
}
