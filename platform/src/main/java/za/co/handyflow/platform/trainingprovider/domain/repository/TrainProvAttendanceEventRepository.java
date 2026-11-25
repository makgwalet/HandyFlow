package za.co.handyflow.platform.trainingprovider.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.shared.TenantId;
import za.co.handyflow.platform.trainingprovider.domain.model.TrainProvAttendanceEvent;

import java.util.List;
import java.util.UUID;

public interface TrainProvAttendanceEventRepository extends JpaRepository<TrainProvAttendanceEvent, UUID> {

    @Query("SELECT e FROM TrainProvAttendanceEvent e WHERE e.tenantId = :#{#tenantId.value} AND e.attendanceId = :attendanceId ORDER BY e.actedAt")
    List<TrainProvAttendanceEvent> findForAttendance(TenantId tenantId, UUID attendanceId);
}
