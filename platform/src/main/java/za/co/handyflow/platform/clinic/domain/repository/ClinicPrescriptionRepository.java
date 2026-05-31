package za.co.handyflow.platform.clinic.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.clinic.domain.model.ClinicPrescription;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.UUID;

public interface ClinicPrescriptionRepository extends JpaRepository<ClinicPrescription, UUID> {

    @Query("SELECT p FROM ClinicPrescription p WHERE p.tenantId = :#{#tenantId.value} AND p.consultationId = :consultationId ORDER BY p.prescribedAt")
    List<ClinicPrescription> findByConsultation(TenantId tenantId, UUID consultationId);

    @Query("SELECT p FROM ClinicPrescription p WHERE p.tenantId = :#{#tenantId.value} AND p.patientId = :patientId ORDER BY p.prescribedAt DESC")
    List<ClinicPrescription> findByPatient(TenantId tenantId, UUID patientId);
}