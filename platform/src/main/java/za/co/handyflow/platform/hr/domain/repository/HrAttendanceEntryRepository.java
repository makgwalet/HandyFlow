package za.co.handyflow.platform.hr.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.hr.domain.model.HrAttendanceEntry;
import za.co.handyflow.platform.shared.TenantId;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface HrAttendanceEntryRepository extends JpaRepository<HrAttendanceEntry, UUID> {

    @Query("SELECT e FROM HrAttendanceEntry e WHERE e.tenantId = :#{#tenantId.value} AND e.id = :id")
    Optional<HrAttendanceEntry> findByTenantAndId(TenantId tenantId, UUID id);

    @Query("""
            SELECT e FROM HrAttendanceEntry e WHERE e.tenantId = :#{#tenantId.value} AND e.employeeId = :employeeId
              AND e.status = 'ACTIVE' AND e.clockOut IS NULL""")
    Optional<HrAttendanceEntry> openFor(TenantId tenantId, UUID employeeId);

    @Query("""
            SELECT e FROM HrAttendanceEntry e WHERE e.tenantId = :#{#tenantId.value} AND e.employeeId = :employeeId
              AND e.status = 'ACTIVE' AND e.workDate BETWEEN :from AND :to ORDER BY e.clockIn""")
    List<HrAttendanceEntry> forEmployee(TenantId tenantId, UUID employeeId, LocalDate from, LocalDate to);

    @Query("""
            SELECT e FROM HrAttendanceEntry e WHERE e.tenantId = :#{#tenantId.value}
              AND e.status = 'ACTIVE' AND e.workDate BETWEEN :from AND :to ORDER BY e.employeeId, e.clockIn""")
    List<HrAttendanceEntry> forTenant(TenantId tenantId, LocalDate from, LocalDate to);

    /** Entries that overlap the span, other than the one being changed (pass a random id when adding). */
    @Query("""
            SELECT e FROM HrAttendanceEntry e WHERE e.tenantId = :#{#tenantId.value} AND e.employeeId = :employeeId
              AND e.status = 'ACTIVE' AND e.id <> :exceptId AND e.clockIn < :to AND (e.clockOut IS NULL OR e.clockOut > :from)""")
    List<HrAttendanceEntry> overlapping(TenantId tenantId, UUID employeeId, java.time.Instant from, java.time.Instant to, UUID exceptId);
}
