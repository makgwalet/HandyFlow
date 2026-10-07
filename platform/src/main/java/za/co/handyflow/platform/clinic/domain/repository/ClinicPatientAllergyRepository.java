package za.co.handyflow.platform.clinic.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.clinic.domain.model.ClinicPatientAllergy;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ClinicPatientAllergyRepository extends JpaRepository<ClinicPatientAllergy, UUID> {

    @Query("SELECT x FROM ClinicPatientAllergy x WHERE x.tenantId = :#{#tenantId.value} AND x.patientId = :patientId ORDER BY x.createdAt DESC")
    List<ClinicPatientAllergy> findByPatient(TenantId tenantId, UUID patientId);

    @Query("SELECT x FROM ClinicPatientAllergy x WHERE x.tenantId = :#{#tenantId.value} AND x.patientId = :patientId AND x.id = :id")
    Optional<ClinicPatientAllergy> findOne(TenantId tenantId, UUID patientId, UUID id);
}
