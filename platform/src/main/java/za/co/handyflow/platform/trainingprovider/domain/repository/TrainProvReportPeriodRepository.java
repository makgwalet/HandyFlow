package za.co.handyflow.platform.trainingprovider.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.shared.TenantId;
import za.co.handyflow.platform.trainingprovider.domain.model.TrainProvReportPeriod;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TrainProvReportPeriodRepository extends JpaRepository<TrainProvReportPeriod, UUID> {

    @Query("""
        SELECT p FROM TrainProvReportPeriod p WHERE p.tenantId = :#{#tenantId.value}
        AND (CAST(:obligationId AS uuid) IS NULL OR p.obligationId = :obligationId)
        AND (:status IS NULL OR p.status = :status)
        ORDER BY p.dueOn, p.periodNo
        """)
    List<TrainProvReportPeriod> search(TenantId tenantId, UUID obligationId, String status);

    @Query("SELECT p FROM TrainProvReportPeriod p WHERE p.tenantId = :#{#tenantId.value} AND p.id = :id")
    Optional<TrainProvReportPeriod> findForTenant(TenantId tenantId, UUID id);

    @Query("""
        SELECT COUNT(p) > 0 FROM TrainProvReportPeriod p WHERE p.tenantId = :#{#tenantId.value} AND p.obligationId = :obligationId
        AND p.periodStart = :start AND p.periodEnd = :end AND p.status <> 'VOID'
        """)
    boolean windowTaken(TenantId tenantId, UUID obligationId, LocalDate start, LocalDate end);
}
