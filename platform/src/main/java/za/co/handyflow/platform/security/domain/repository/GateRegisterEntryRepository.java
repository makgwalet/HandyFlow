// security/domain/repository/GateRegisterEntryRepository.java

package za.co.handyflow.platform.security.domain.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.security.domain.model.GateRegisterEntry;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface GateRegisterEntryRepository extends JpaRepository<GateRegisterEntry, UUID> {

    @Query("SELECT e FROM GateRegisterEntry e WHERE e.id = :id AND e.tenantId = :tenantId")
    Optional<GateRegisterEntry> findByIdAndTenant(UUID id, TenantId tenantId);

    /** Backs the on-site list — supervisor view, client portal count, and the evacuation-muster use case. */
    @Query("SELECT e FROM GateRegisterEntry e WHERE e.tenantId = :tenantId AND e.siteId = :siteId " +
            "AND e.loggedOutAt IS NULL ORDER BY e.loggedInAt DESC")
    List<GateRegisterEntry> findOnSiteBySite(TenantId tenantId, UUID siteId);

    /** Backs the gate log history view — paginated. */
    @Query("SELECT e FROM GateRegisterEntry e WHERE e.tenantId = :tenantId AND e.siteId = :siteId " +
            "AND e.loggedInAt BETWEEN :from AND :to ORDER BY e.loggedInAt DESC")
    Page<GateRegisterEntry> findBySiteAndPeriod(TenantId tenantId, UUID siteId,
                                                Instant from, Instant to, Pageable pageable);

    /**
     * FIX: Gate Access & Registry, Step 6. Non-paginated version of the
     * same period query, for the Site Access/Visitor Report — a report
     * needs the whole month's data to compute its own totals/breakdowns,
     * not one page of it.
     */
    @Query("SELECT e FROM GateRegisterEntry e WHERE e.tenantId = :tenantId AND e.siteId = :siteId " +
            "AND e.loggedInAt BETWEEN :from AND :to ORDER BY e.loggedInAt ASC")
    List<GateRegisterEntry> findAllBySiteAndPeriod(TenantId tenantId, UUID siteId, Instant from, Instant to);

    /**
     * Backs the overstay scheduler — edge-triggered dedup, same shape as
     * ShiftRepository's own find*NotYetAlerted() methods: only rows past
     * the threshold that haven't already had an alert marked.
     * <p>
     * FIX: tenant-scoped — NoShowAlertScheduler's confirmed real
     * structure processes tenants one at a time in a loop
     * (siteRepository.findDistinctActiveTenantIds(), then per-tenant),
     * not all tenants in one unscoped query. This method needed the
     * same shape to actually be usable that way.
     */
    @Query("SELECT e FROM GateRegisterEntry e WHERE e.tenantId = :tenantId AND e.status = 'ON_SITE' " +
            "AND e.loggedInAt < :threshold AND e.overstayAlertSentAt IS NULL")
    List<GateRegisterEntry> findOverstayedNotYetAlerted(TenantId tenantId, Instant threshold);
}