package za.co.handyflow.platform.clinic.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.clinic.domain.model.ClinicPatientCondition;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ClinicPatientConditionRepository extends JpaRepository<ClinicPatientCondition, UUID> {

    @Query("SELECT x FROM ClinicPatientCondition x WHERE x.tenantId = :#{#tenantId.value} AND x.patientId = :patientId ORDER BY x.createdAt DESC")
    List<ClinicPatientCondition> findByPatient(TenantId tenantId, UUID patientId);

    @Query("SELECT x FROM ClinicPatientCondition x WHERE x.tenantId = :#{#tenantId.value} AND x.patientId = :patientId AND x.id = :id")
    Optional<ClinicPatientCondition> findOne(TenantId tenantId, UUID patientId, UUID id);
}
