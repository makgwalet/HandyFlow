package za.co.handyflow.platform.clinic.domain.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.clinic.domain.model.ClinicConsultation;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ClinicConsultationRepository extends JpaRepository<ClinicConsultation, UUID> {

    @Query("SELECT c FROM ClinicConsultation c WHERE c.tenantId = :#{#tenantId.value} AND c.patientId = :patientId AND c.deletedAt IS NULL ORDER BY c.consultedAt DESC")
    List<ClinicConsultation> findByPatient(TenantId tenantId, UUID patientId);

    @Query("SELECT c FROM ClinicConsultation c WHERE c.tenantId = :#{#tenantId.value} AND c.status IN ('DRAFT','NURSE_IN_PROGRESS','RETURNED_TO_NURSE') AND c.deletedAt IS NULL ORDER BY c.updatedAt DESC")
    List<ClinicConsultation> findDrafts(TenantId tenantId);

    @Query("SELECT c FROM ClinicConsultation c WHERE c.tenantId = :#{#tenantId.value} AND c.status IN ('READY_FOR_DOCTOR','DOCTOR_REVIEWING','DOCTOR_COMPLETED') AND c.deletedAt IS NULL ORDER BY c.updatedAt ASC")
    List<ClinicConsultation> findHandoffQueue(TenantId tenantId);

    @Query("SELECT c FROM ClinicConsultation c WHERE c.tenantId = :#{#tenantId.value} AND c.appointmentId = :appointmentId AND c.status IN ('DRAFT','NURSE_IN_PROGRESS','READY_FOR_DOCTOR','DOCTOR_REVIEWING','RETURNED_TO_NURSE','DOCTOR_COMPLETED') AND c.deletedAt IS NULL")
    List<ClinicConsultation> findUnsignedByAppointment(TenantId tenantId, UUID appointmentId);

    @Query("SELECT c FROM ClinicConsultation c WHERE c.tenantId = :#{#tenantId.value} AND c.id = :id AND c.deletedAt IS NULL")
    Optional<ClinicConsultation> findActiveById(TenantId tenantId, UUID id);

    @Query("SELECT c FROM ClinicConsultation c WHERE c.tenantId = :#{#tenantId.value} AND c.deletedAt IS NULL ORDER BY c.consultedAt DESC")
    Page<ClinicConsultation> findAllActive(TenantId tenantId, Pageable pageable);

    // FIX #8 — for the /billing/consultations?unbilled=true endpoint
    @Query("SELECT c FROM ClinicConsultation c WHERE c.tenantId = :#{#tenantId.value} AND c.deletedAt IS NULL AND c.status IN ('SIGNED','LOCKED') AND c.billed = false ORDER BY c.consultedAt DESC")
    Page<ClinicConsultation> findAllUnbilled(TenantId tenantId, Pageable pageable);
}
