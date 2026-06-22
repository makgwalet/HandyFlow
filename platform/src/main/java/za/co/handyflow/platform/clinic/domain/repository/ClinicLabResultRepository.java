package za.co.handyflow.platform.clinic.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.clinic.domain.model.ClinicLabResult;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ClinicLabResultRepository extends JpaRepository<ClinicLabResult, UUID> {

    @Query("SELECT r FROM ClinicLabResult r WHERE r.tenantId = :#{#tenantId.value} ORDER BY r.receivedAt DESC")
    List<ClinicLabResult> findAll(TenantId tenantId);

    @Query("SELECT r FROM ClinicLabResult r WHERE r.tenantId = :#{#tenantId.value} AND r.status = :status ORDER BY r.receivedAt DESC")
    List<ClinicLabResult> findByStatus(TenantId tenantId, String status);

    @Query("SELECT r FROM ClinicLabResult r WHERE r.tenantId = :#{#tenantId.value} AND r.patientId = :patientId ORDER BY r.receivedAt DESC")
    List<ClinicLabResult> findByPatient(TenantId tenantId, UUID patientId);

    @Query("SELECT r FROM ClinicLabResult r WHERE r.tenantId = :#{#tenantId.value} AND r.id = :id")
    Optional<ClinicLabResult> findByIdAndTenant(TenantId tenantId, UUID id);
}
