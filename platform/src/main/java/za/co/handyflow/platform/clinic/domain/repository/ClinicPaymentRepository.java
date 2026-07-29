package za.co.handyflow.platform.clinic.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.clinic.domain.model.ClinicPayment;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface ClinicPaymentRepository extends JpaRepository<ClinicPayment, UUID> {

    @Query("SELECT p FROM ClinicPayment p WHERE p.tenantId = :#{#tenantId.value} ORDER BY p.recordedAt DESC")
    List<ClinicPayment> findAllByTenant(TenantId tenantId);

    @Query("SELECT p FROM ClinicPayment p WHERE p.tenantId = :#{#tenantId.value} AND p.recordedAt >= :from AND p.recordedAt < :to ORDER BY p.recordedAt DESC")
    List<ClinicPayment> findByPeriod(TenantId tenantId, Instant from, Instant to);

    @Query("SELECT p FROM ClinicPayment p WHERE p.tenantId = :#{#tenantId.value} AND p.patientId = :patientId ORDER BY p.recordedAt DESC")
    List<ClinicPayment> findByPatient(TenantId tenantId, UUID patientId);
}