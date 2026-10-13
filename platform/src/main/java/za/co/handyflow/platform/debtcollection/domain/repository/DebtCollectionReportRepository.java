package za.co.handyflow.platform.debtcollection.domain.repository;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import za.co.handyflow.platform.debtcollection.domain.model.DebtCollectionCase;
import za.co.handyflow.platform.shared.TenantId;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Read-only aggregate queries for the collections dashboard. A marker Repository (no CRUD) because these
 * span several aggregates (contact logs, work items, PTPs). Rows come back as Object[] and are mapped in
 * DebtCollectionDashboardService — column order is documented on each query.
 */
public interface DebtCollectionReportRepository extends Repository<DebtCollectionCase, UUID> {

    /** [userId UUID, userName String, contacts Long] — contact attempts logged in the window, per collector. */
    @Query("""
        SELECT l.recordedByUserId, MAX(l.recordedByUserName), COUNT(l)
        FROM CollectionContactLog l
        WHERE l.tenantId = :tenantId AND l.contactDate BETWEEN :from AND :to
        GROUP BY l.recordedByUserId
        """)
    List<Object[]> contactCounts(TenantId tenantId, LocalDate from, LocalDate to);

    /** [userId UUID, userName String, open Long, overdue Long] — OPEN (actionable) work items per assignee; overdue = dueDate before today. */
    @Query("""
        SELECT w.assignedToUserId, MAX(w.assignedToUserName), COUNT(w),
               COUNT(CASE WHEN w.dueDate < :today THEN 1 ELSE NULL END)
        FROM WorkItem w
        WHERE w.tenantId = :tenantId AND w.status = 'OPEN'
        GROUP BY w.assignedToUserId
        """)
    List<Object[]> workItemCounts(TenantId tenantId, LocalDate today);

    /**
     * [userId UUID, userName String, status PtpStatus, count Long, promised BigDecimal, received BigDecimal]
     * — PTPs resolved in the window, grouped by who recorded them and by outcome; received is capped at the
     * promised amount per promise (same convention as PtpService.metrics).
     */
    @Query("""
        SELECT p.createdBy, MAX(p.createdByName), p.status, COUNT(p), SUM(p.amount),
               SUM(CASE WHEN p.amountReceived < p.amount THEN p.amountReceived ELSE p.amount END)
        FROM PromiseToPay p
        WHERE p.tenantId = :tenantId AND p.status IN ('KEPT','PARTIALLY_KEPT','BROKEN')
          AND p.resolvedDate BETWEEN :from AND :to
        GROUP BY p.createdBy, p.status
        """)
    List<Object[]> ptpOutcomesByCreator(TenantId tenantId, LocalDate from, LocalDate to);
}
