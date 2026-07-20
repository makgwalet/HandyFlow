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
}