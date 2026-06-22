package za.co.handyflow.platform.clinic.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.clinic.domain.model.ClinicMedicalAid;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ClinicMedicalAidRepository extends JpaRepository<ClinicMedicalAid, UUID> {

    // Active medical aid record for a patient (most practices have one active plan at a time)
    @Query("""
        SELECT m FROM ClinicMedicalAid m
        WHERE m.tenantId = :#{#tenantId.value}
          AND m.patientId = :patientId
          AND m.active = true
        ORDER BY m.createdAt DESC
        """)
    List<ClinicMedicalAid> findActiveByPatient(TenantId tenantId, UUID patientId);

    @Query("""
        SELECT m FROM ClinicMedicalAid m
        WHERE m.tenantId  = :#{#tenantId.value}
          AND m.patientId = :patientId
        ORDER BY m.active DESC, m.createdAt DESC
        """)
    List<ClinicMedicalAid> findAllByPatient(TenantId tenantId, UUID patientId);
}
