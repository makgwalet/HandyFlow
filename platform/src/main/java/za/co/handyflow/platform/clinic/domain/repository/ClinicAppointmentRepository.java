package za.co.handyflow.platform.clinic.domain.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.clinic.domain.model.ClinicAppointment;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ClinicAppointmentRepository extends JpaRepository<ClinicAppointment, UUID> {

    @Query("""
        SELECT a FROM ClinicAppointment a
        WHERE a.tenantId = :#{#tenantId.value} AND a.deletedAt IS NULL
        ORDER BY a.scheduledAt DESC
        """)
    Page<ClinicAppointment> findAllActive(TenantId tenantId, Pageable pageable);

    @Query("""
        SELECT a FROM ClinicAppointment a
        WHERE a.tenantId = :#{#tenantId.value} AND a.deletedAt IS NULL
        AND a.status = :status
        ORDER BY a.scheduledAt DESC
        """)
    Page<ClinicAppointment> findAllActiveByStatus(TenantId tenantId, String status, Pageable pageable);

    @Query("SELECT a FROM ClinicAppointment a WHERE a.tenantId = :#{#tenantId.value} AND a.patientId = :patientId AND a.deletedAt IS NULL ORDER BY a.scheduledAt DESC")
    List<ClinicAppointment> findByPatient(TenantId tenantId, UUID patientId);

    @Query("SELECT a FROM ClinicAppointment a WHERE a.tenantId = :#{#tenantId.value} AND a.id = :id AND a.deletedAt IS NULL")
    Optional<ClinicAppointment> findActiveById(TenantId tenantId, UUID id);

    @Query("SELECT a FROM ClinicAppointment a WHERE a.tenantId = :#{#tenantId.value} AND a.scheduledAt >= :from AND a.scheduledAt < :to AND a.deletedAt IS NULL ORDER BY a.scheduledAt")
    List<ClinicAppointment> findByDateRange(TenantId tenantId, Instant from, Instant to);
}