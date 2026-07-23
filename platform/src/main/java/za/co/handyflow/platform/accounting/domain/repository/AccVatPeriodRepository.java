package za.co.handyflow.platform.accounting.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import za.co.handyflow.platform.accounting.domain.model.AccVatPeriod;
import za.co.handyflow.platform.shared.TenantId;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AccVatPeriodRepository extends JpaRepository<AccVatPeriod, UUID> {

    @Query("SELECT v FROM AccVatPeriod v WHERE v.tenantId = :#{#tenantId.value} ORDER BY v.periodStart DESC")
    List<AccVatPeriod> findAll(TenantId tenantId);

    @Query("SELECT v FROM AccVatPeriod v WHERE v.tenantId = :#{#tenantId.value} AND v.status = 'OPEN' ORDER BY v.periodStart DESC")
    Optional<AccVatPeriod> findOpenPeriod(TenantId tenantId);

    @Query("SELECT v FROM AccVatPeriod v WHERE v.tenantId = :#{#tenantId.value} AND v.id = :id")
    Optional<AccVatPeriod> findByTenantAndId(TenantId tenantId, UUID id);

    /**
     * Find all OPEN VAT periods across all tenants ending within the given date range.
     * Used by AccountingNotificationScheduler to find periods closing soon.
     */
    @Query("""
        SELECT v FROM AccVatPeriod v
        WHERE v.status = 'OPEN'
        AND v.periodEnd >= :from
        AND v.periodEnd <= :to
        """)
    List<AccVatPeriod> findOpenPeriodsEndingBetween(
            @Param("from") LocalDate from,
            @Param("to")   LocalDate to);

    /**
     * Find all OPEN VAT periods across all tenants past their own end
     * date — deliberately NOT a stored "OVERDUE" status. This module has
     * already hit two real bugs from guessing at unverified VARCHAR-
     * length/CHECK-constraint values on status-like columns (entry_type,
     * ap_bills status) — "overdue" here is just a query condition
     * (status still 'OPEN', periodEnd in the past), computed fresh each
     * time the scheduler runs, with no schema risk at all.
     */
    @Query("""
        SELECT v FROM AccVatPeriod v
        WHERE v.status = 'OPEN'
        AND v.periodEnd < :today
        """)
    List<AccVatPeriod> findOpenPeriodsOverdue(@Param("today") LocalDate today);

}