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

    @Query("SELECT c FROM ClinicConsultation c WHERE c.tenantId = :#{#tenantId.value} AND c.id = :id AND c.deletedAt IS NULL")
    Optional<ClinicConsultation> findActiveById(TenantId tenantId, UUID id);

    @Query("SELECT c FROM ClinicConsultation c WHERE c.tenantId = :#{#tenantId.value} AND c.deletedAt IS NULL ORDER BY c.consultedAt DESC")
    Page<ClinicConsultation> findAllActive(TenantId tenantId, Pageable pageable);

    // FIX #8 — for the /billing/consultations?unbilled=true endpoint
    @Query("SELECT c FROM ClinicConsultation c WHERE c.tenantId = :#{#tenantId.value} AND c.deletedAt IS NULL AND c.billed = false ORDER BY c.consultedAt DESC")
    Page<ClinicConsultation> findAllUnbilled(TenantId tenantId, Pageable pageable);
}
