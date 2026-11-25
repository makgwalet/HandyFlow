package za.co.handyflow.platform.trainingprovider.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.shared.TenantId;
import za.co.handyflow.platform.trainingprovider.domain.model.TrainProvReportObligation;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TrainProvReportObligationRepository extends JpaRepository<TrainProvReportObligation, UUID> {

    @Query("""
        SELECT o FROM TrainProvReportObligation o WHERE o.tenantId = :#{#tenantId.value}
        AND (CAST(:authorityId AS uuid) IS NULL OR o.authorityId = :authorityId)
        AND (:status IS NULL OR o.status = :status)
        ORDER BY o.obligationNo DESC
        """)
    List<TrainProvReportObligation> search(TenantId tenantId, UUID authorityId, String status);

    @Query("SELECT o FROM TrainProvReportObligation o WHERE o.tenantId = :#{#tenantId.value} AND o.id = :id")
    Optional<TrainProvReportObligation> findForTenant(TenantId tenantId, UUID id);

    @Query("""
        SELECT COUNT(o) > 0 FROM TrainProvReportObligation o WHERE o.tenantId = :#{#tenantId.value} AND o.authorityId = :authorityId
        AND ((CAST(:programmeId AS uuid) IS NULL AND o.programmeId IS NULL) OR o.programmeId = :programmeId)
        AND LOWER(o.name) = LOWER(:name) AND o.status = 'ACTIVE' AND (CAST(:excludeId AS uuid) IS NULL OR o.id <> :excludeId)
        """)
    boolean liveNameTaken(TenantId tenantId, UUID authorityId, UUID programmeId, String name, UUID excludeId);
}
