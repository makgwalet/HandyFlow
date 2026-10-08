package za.co.handyflow.platform.clinic.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.clinic.domain.model.ClinicPatientFamilyHistory;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ClinicPatientFamilyHistoryRepository extends JpaRepository<ClinicPatientFamilyHistory, UUID> {

    @Query("SELECT x FROM ClinicPatientFamilyHistory x WHERE x.tenantId = :#{#tenantId.value} AND x.patientId = :patientId ORDER BY x.createdAt DESC")
    List<ClinicPatientFamilyHistory> findByPatient(TenantId tenantId, UUID patientId);

    @Query("SELECT x FROM ClinicPatientFamilyHistory x WHERE x.tenantId = :#{#tenantId.value} AND x.patientId = :patientId AND x.id = :id")
    Optional<ClinicPatientFamilyHistory> findOne(TenantId tenantId, UUID patientId, UUID id);
}
