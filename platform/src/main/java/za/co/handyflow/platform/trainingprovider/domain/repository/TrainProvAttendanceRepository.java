package za.co.handyflow.platform.trainingprovider.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.shared.TenantId;
import za.co.handyflow.platform.trainingprovider.domain.model.TrainProvAttendance;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TrainProvAttendanceRepository extends JpaRepository<TrainProvAttendance, UUID> {

    @Query("SELECT a FROM TrainProvAttendance a WHERE a.tenantId = :#{#tenantId.value} AND a.slotId = :slotId")
    List<TrainProvAttendance> findForSlot(TenantId tenantId, UUID slotId);

    @Query("SELECT a FROM TrainProvAttendance a WHERE a.tenantId = :#{#tenantId.value} AND a.id = :id")
    Optional<TrainProvAttendance> findForTenant(TenantId tenantId, UUID id);

    @Query("SELECT a FROM TrainProvAttendance a WHERE a.tenantId = :#{#tenantId.value} AND a.slotId = :slotId AND a.memberId = :memberId")
    Optional<TrainProvAttendance> findForSlotAndMember(TenantId tenantId, UUID slotId, UUID memberId);

    @Query("""
        SELECT a FROM TrainProvAttendance a WHERE a.tenantId = :#{#tenantId.value}
        AND a.slotId IN (SELECT s.id FROM TrainProvTimetableSlot s WHERE s.tenantId = :#{#tenantId.value} AND s.cohortId = :cohortId)
        """)
    List<TrainProvAttendance> findForCohort(TenantId tenantId, UUID cohortId);
}
