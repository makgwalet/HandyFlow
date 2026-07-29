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

    /**
     * FIX: "no appointment reminders" gap — cross-tenant by design, same
     * pattern as QuoteRepository.findExpiredQuotes and
     * RecurringScheduleRepository.findDueSchedules: this is a nightly batch
     * job's query, not a tenant-scoped read, so no tenantId filter.
     * Only SCHEDULED/CONFIRMED appointments in the future window that
     * haven't already had a reminder sent.
     */
    @Query("""
        SELECT a FROM ClinicAppointment a
        WHERE a.status IN ('SCHEDULED', 'CONFIRMED')
        AND a.reminderSentAt IS NULL
        AND a.scheduledAt >= :from AND a.scheduledAt < :to
        AND a.deletedAt IS NULL
        """)
    List<ClinicAppointment> findDueForReminder(Instant from, Instant to);
}