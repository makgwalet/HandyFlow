package za.co.handyflow.platform.clinic.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.clinic.domain.model.ClinicPatientMedication;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ClinicPatientMedicationRepository extends JpaRepository<ClinicPatientMedication, UUID> {

    @Query("SELECT x FROM ClinicPatientMedication x WHERE x.tenantId = :#{#tenantId.value} AND x.patientId = :patientId ORDER BY x.createdAt DESC")
    List<ClinicPatientMedication> findByPatient(TenantId tenantId, UUID patientId);

    @Query("SELECT x FROM ClinicPatientMedication x WHERE x.tenantId = :#{#tenantId.value} AND x.patientId = :patientId AND x.id = :id")
    Optional<ClinicPatientMedication> findOne(TenantId tenantId, UUID patientId, UUID id);
}
