package za.co.handyflow.platform.legalcompliance.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.legalcompliance.domain.model.*;
import za.co.handyflow.platform.shared.TenantId;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PiaRepository extends JpaRepository<Pia, UUID> {

    @Query("SELECT x FROM Pia x WHERE x.tenantId = :tenantId AND x.id = :id")
    Optional<Pia> findForTenant(TenantId tenantId, UUID id);

    @Query("SELECT x FROM Pia x WHERE x.tenantId = :tenantId AND (:status IS NULL OR x.status = :status) ORDER BY x.createdAt DESC")
    List<Pia> search(TenantId tenantId, PiaStatus status);

    /** Cross-tenant — daily sweep: approved assessments whose review date has arrived or is near. */
    @Query("SELECT x FROM Pia x WHERE x.status = za.co.handyflow.platform.legalcompliance.domain.model.PiaStatus.APPROVED AND x.nextReviewDue IS NOT NULL AND x.nextReviewDue <= :horizon")
    List<Pia> findReviewDueAcrossTenants(LocalDate horizon);

    @Query("SELECT COUNT(x) FROM Pia x WHERE x.tenantId = :tenantId AND x.popiaActivityId = :activityId AND x.status = za.co.handyflow.platform.legalcompliance.domain.model.PiaStatus.APPROVED")
    long countApprovedForActivity(TenantId tenantId, UUID activityId);

    @Query(value = "SELECT nextval('legalcompliance_ref_seq')", nativeQuery = true)
    long nextRef();
}
