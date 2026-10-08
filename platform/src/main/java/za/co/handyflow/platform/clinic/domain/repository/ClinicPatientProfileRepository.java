package za.co.handyflow.platform.clinic.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.clinic.domain.model.ClinicPatientProfile;
import za.co.handyflow.platform.shared.TenantId;

import java.util.Optional;
import java.util.UUID;

public interface ClinicPatientProfileRepository extends JpaRepository<ClinicPatientProfile, UUID> {

    @Query("SELECT x FROM ClinicPatientProfile x WHERE x.tenantId = :#{#tenantId.value} AND x.patientId = :patientId")
    Optional<ClinicPatientProfile> findOne(TenantId tenantId, UUID patientId);
}
