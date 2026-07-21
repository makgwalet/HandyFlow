package za.co.handyflow.platform.accountant.domain.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import za.co.handyflow.platform.accountant.domain.model.TimeEntry;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Repository
public interface TimeEntryRepository extends JpaRepository<TimeEntry, UUID> {

    @Query("""
        SELECT t FROM AccountantTimeEntry t
        WHERE t.clientId = :clientId
        ORDER BY t.entryDate DESC
    """)
    Page<TimeEntry> findByClient(@Param("clientId") UUID clientId, Pageable pageable);

    /** All unbilled (WIP) entries for a specific client. */
    @Query("""
        SELECT t FROM AccountantTimeEntry t
        WHERE t.clientId = :clientId
          AND t.status = 'UNBILLED'
        ORDER BY t.entryDate ASC
    """)
    List<TimeEntry> findUnbilledByClient(@Param("clientId") UUID clientId);

    /** All unbilled entries across all clients — for the firm WIP summary. */
    @Query("""
        SELECT t FROM AccountantTimeEntry t
        WHERE t.tenantId = :tenantId
          AND t.status = 'UNBILLED'
        ORDER BY t.clientId, t.entryDate ASC
    """)
    List<TimeEntry> findUnbilledByTenant(@Param("tenantId") UUID tenantId);

    /**
     * WIP value for a client — sum of hours × hourly_rate for all UNBILLED entries.
     * Returns null if no unbilled entries exist (caller should treat null as ZERO).
     */
    @Query("""
        SELECT SUM(t.hours * t.hourlyRate)
        FROM AccountantTimeEntry t
        WHERE t.clientId = :clientId
          AND t.status = 'UNBILLED'
    """)
    BigDecimal sumWipByClient(@Param("clientId") UUID clientId);

    /** Entries for a practitioner within a date range — for utilisation reporting. */
    @Query("""
        SELECT t FROM AccountantTimeEntry t
        WHERE t.tenantId       = :tenantId
          AND t.practitionerId = :practitionerId
          AND t.entryDate BETWEEN :from AND :to
        ORDER BY t.entryDate ASC
    """)
    List<TimeEntry> findByPractitioner(@Param("tenantId") UUID tenantId,
                                       @Param("practitionerId") UUID practitionerId,
                                       @Param("from") LocalDate from,
                                       @Param("to")   LocalDate to);

    /**
     * NEW: closes the audit's "time entry edit/delete" gap. No
     * single-record tenant-safe lookup existed on this repository at
     * all before this — every other query returns a list scoped by
     * client/tenant, but nothing let a single entry be fetched and
     * verified as belonging to the caller's tenant before editing or
     * deleting it.
     */
    @Query("SELECT t FROM AccountantTimeEntry t WHERE t.tenantId = :tenantId AND t.id = :id")
    java.util.Optional<TimeEntry> findByTenantIdAndId(@Param("tenantId") UUID tenantId, @Param("id") UUID id);

    /**
     * NEW: closes the accountant module audit's "staff-level time
     * report" gap — the actual aggregate answering "what did each team
     * member bill this period", not just findByPractitioner()'s
     * one-practitioner-at-a-time detail view. Groups by practitionerId
     * across every client for the tenant. A NULL practitionerId group
     * (historical entries logged before AccountantService.logTime()
     * was fixed to actually capture this) is a real, expected
     * possibility — handled as "Unassigned" in the service layer, not
     * hidden or excluded here.
     */
    interface StaffTimeSummaryProjection {
        UUID getPractitionerId();
        String getPractitionerName();
        BigDecimal getTotalHours();
        BigDecimal getBillableHours();
        BigDecimal getTotalBilled();
        Long getEntryCount();
    }

    @Query("""
        SELECT t.practitionerId as practitionerId,
               MAX(t.practitionerName) as practitionerName,
               COALESCE(SUM(t.hours), 0) as totalHours,
               COALESCE(SUM(CASE WHEN t.billable = true THEN t.hours ELSE 0 END), 0) as billableHours,
               COALESCE(SUM(CASE WHEN t.billable = true THEN t.hours * t.hourlyRate ELSE 0 END), 0) as totalBilled,
               COUNT(t) as entryCount
        FROM AccountantTimeEntry t
        WHERE t.tenantId  = :tenantId
          AND t.entryDate BETWEEN :from AND :to
        GROUP BY t.practitionerId
        ORDER BY totalHours DESC
    """)
    List<StaffTimeSummaryProjection> findStaffSummary(@Param("tenantId") UUID tenantId,
                                                      @Param("from") LocalDate from,
                                                      @Param("to") LocalDate to);
}