package za.co.handyflow.platform.trainingprovider.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.shared.TenantId;
import za.co.handyflow.platform.trainingprovider.domain.model.TrainProvAppointmentSnapshot;

import java.util.List;
import java.util.UUID;

public interface TrainProvAppointmentSnapshotRepository extends JpaRepository<TrainProvAppointmentSnapshot, UUID> {

    @Query("SELECT s FROM TrainProvAppointmentSnapshot s WHERE s.tenantId = :#{#tenantId.value} AND s.subjectType = :subjectType AND s.subjectId = :subjectId ORDER BY s.capturedAt")
    List<TrainProvAppointmentSnapshot> findForSubject(TenantId tenantId, String subjectType, UUID subjectId);
}
