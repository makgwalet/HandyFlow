package za.co.handyflow.platform.trainingprovider.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.shared.TenantId;
import za.co.handyflow.platform.trainingprovider.domain.model.TrainProvPractitionerAppointmentEvent;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TrainProvPractitionerAppointmentEventRepository extends JpaRepository<TrainProvPractitionerAppointmentEvent, UUID> {

    @Query("SELECT e FROM TrainProvPractitionerAppointmentEvent e WHERE e.tenantId = :#{#tenantId.value} AND e.appointmentId = :appointmentId ORDER BY e.actedAt")
    List<TrainProvPractitionerAppointmentEvent> findForAppointment(TenantId tenantId, UUID appointmentId);
}
