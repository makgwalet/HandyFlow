package za.co.handyflow.platform.clinic.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.clinic.domain.model.ClinicPatientConsent;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.UUID;

public interface ClinicPatientConsentRepository extends JpaRepository<ClinicPatientConsent, UUID> {

    @Query("SELECT c FROM ClinicPatientConsent c WHERE c.tenantId = :#{#tenantId.value} AND c.patientId = :patientId ORDER BY c.createdAt DESC")
    List<ClinicPatientConsent> findByPatient(TenantId tenantId, UUID patientId);
}