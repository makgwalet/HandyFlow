package za.co.handyflow.platform.trainingprovider.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.shared.TenantId;
import za.co.handyflow.platform.trainingprovider.domain.model.TrainProvPractitionerAppointment;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TrainProvPractitionerAppointmentRepository extends JpaRepository<TrainProvPractitionerAppointment, UUID> {

    @Query("SELECT a FROM TrainProvPractitionerAppointment a WHERE a.tenantId = :#{#tenantId.value} AND a.practitionerId = :practitionerId ORDER BY a.status, a.role, a.createdAt")
    List<TrainProvPractitionerAppointment> findForPractitioner(TenantId tenantId, UUID practitionerId);

    @Query("SELECT a FROM TrainProvPractitionerAppointment a WHERE a.tenantId = :#{#tenantId.value} AND a.id = :id")
    Optional<TrainProvPractitionerAppointment> findForTenant(TenantId tenantId, UUID id);

    @Query("""
        SELECT COUNT(a) > 0 FROM TrainProvPractitionerAppointment a WHERE a.tenantId = :#{#tenantId.value}
        AND a.practitionerId = :practitionerId AND a.role = :role AND a.programmeId = :programmeId AND a.status = 'ACTIVE'
        """)
    boolean liveExists(TenantId tenantId, UUID practitionerId, String role, UUID programmeId);

    @Query("""
        SELECT a FROM TrainProvPractitionerAppointment a WHERE a.tenantId = :#{#tenantId.value}
        AND a.programmeId = :programmeId AND a.role = :role AND a.status = 'ACTIVE'
        """)
    List<TrainProvPractitionerAppointment> findLiveForProgramme(TenantId tenantId, UUID programmeId, String role);

    @Query("SELECT COUNT(a) FROM TrainProvPractitionerAppointment a WHERE a.tenantId = :#{#tenantId.value} AND a.practitionerId = :practitionerId AND a.status = 'ACTIVE'")
    long countLive(TenantId tenantId, UUID practitionerId);
}
