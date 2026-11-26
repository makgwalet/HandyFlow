package za.co.handyflow.platform.payrollbureau.domain.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import za.co.handyflow.platform.payrollbureau.domain.model.PayRun;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PayRunRepository extends JpaRepository<PayRun, UUID> {

    @Query("SELECT r FROM PayRun r WHERE r.tenantId = :tenantId AND r.payClientId = :payClientId ORDER BY r.periodStart DESC")
    Page<PayRun> findByClient(@Param("tenantId") UUID tenantId, @Param("payClientId") UUID payClientId, Pageable pageable);

    /** Other runs of this client, not reversed, whose period shares a day with the given one. The caller narrows to the same pay group. */
    @Query("""
        SELECT r FROM PayRun r
        WHERE r.tenantId = :tenantId AND r.payClientId = :payClientId AND r.id <> :excludeId
          AND r.status <> 'REVERSED' AND r.periodStart <= :periodEnd AND r.periodEnd >= :periodStart
    """)
    List<PayRun> findOverlapping(@Param("tenantId") UUID tenantId, @Param("payClientId") UUID payClientId,
                                 @Param("excludeId") UUID excludeId, @Param("periodStart") java.time.LocalDate periodStart,
                                 @Param("periodEnd") java.time.LocalDate periodEnd);

    /** Runs of this client that started before the given date and have payslips, newest first. The caller narrows to the same pay group. */
    @Query("""
        SELECT r FROM PayRun r
        WHERE r.tenantId = :tenantId AND r.payClientId = :payClientId AND r.periodStart < :before
          AND r.status IN ('CALCULATED','APPROVED','AWAITING_CLIENT','CLIENT_APPROVED','FINALISED') ORDER BY r.periodStart DESC
    """)
    List<PayRun> findEarlierWithPayslips(@Param("tenantId") UUID tenantId, @Param("payClientId") UUID payClientId,
                                         @Param("before") java.time.LocalDate before);

    @Query("SELECT r FROM PayRun r WHERE r.tenantId = :tenantId AND r.id = :id")
    Optional<PayRun> findByTenantAndId(@Param("tenantId") UUID tenantId, @Param("id") UUID id);

    /** Finalised runs of a client whose pay date falls in the month (reversed runs are left out). */
    @Query("""
        SELECT r FROM PayRun r
        WHERE r.tenantId = :tenantId AND r.payClientId = :clientId AND r.status = 'FINALISED'
          AND r.payDate >= :from AND r.payDate <= :to
        ORDER BY r.payDate ASC, r.periodStart ASC
    """)
    List<PayRun> findFinalisedByPayDate(@Param("tenantId") UUID tenantId, @Param("clientId") UUID clientId,
                                        @Param("from") java.time.LocalDate from, @Param("to") java.time.LocalDate to);

    /** Every client's finalised runs paid in the range, for profitability. */
    @Query("SELECT r FROM PayRun r WHERE r.tenantId = :tenantId AND r.status = 'FINALISED' AND r.payDate >= :from AND r.payDate <= :to")
    List<PayRun> findFinalisedInRange(@Param("tenantId") UUID tenantId, @Param("from") java.time.LocalDate from, @Param("to") java.time.LocalDate to);

    /** The runs a client's portal users may see: those sent to them, approved by them, or finalised. */
    @Query("""
        SELECT r FROM PayRun r
        WHERE r.tenantId = :tenantId AND r.payClientId = :clientId
          AND r.status IN ('AWAITING_CLIENT','CLIENT_APPROVED','FINALISED','REVERSED')
        ORDER BY r.periodStart DESC
    """)
    List<PayRun> findForPortal(@Param("tenantId") UUID tenantId, @Param("clientId") UUID clientId);

    // Cross-tenant by design — schedulers have no TenantContext to read
    // from (they run outside any HTTP request). Same shape as
    // ApBillRepository.findDueSoonAcrossTenants() and
    // RecInterviewRepository.findDueForReminder().
    @Query("""
    SELECT r FROM PayRun r
    WHERE r.status = 'FINALISED'
      AND r.payDate <= :today
      AND r.payslipsAutoSentAt IS NULL
    """)
    List<PayRun> findDueForAutoSend(@Param("today") java.time.LocalDate today);
}