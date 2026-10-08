package za.co.handyflow.platform.clinic.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.clinic.domain.model.ClinicPatientSocialHistory;
import za.co.handyflow.platform.shared.TenantId;

import java.util.Optional;
import java.util.UUID;

public interface ClinicPatientSocialHistoryRepository extends JpaRepository<ClinicPatientSocialHistory, UUID> {

    @Query("SELECT x FROM ClinicPatientSocialHistory x WHERE x.tenantId = :#{#tenantId.value} AND x.patientId = :patientId")
    Optional<ClinicPatientSocialHistory> findForPatient(TenantId tenantId, UUID patientId);
}
