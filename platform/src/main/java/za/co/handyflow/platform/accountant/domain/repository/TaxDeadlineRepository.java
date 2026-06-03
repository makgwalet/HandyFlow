package za.co.handyflow.platform.accountant.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import za.co.handyflow.platform.accountant.domain.model.TaxDeadline;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Repository
public interface TaxDeadlineRepository extends JpaRepository<TaxDeadline, UUID> {

    /** All deadlines for a specific client, newest period first. */
    @Query("""
        SELECT d FROM AccountantTaxDeadline d
        WHERE d.clientId = :clientId
        ORDER BY d.adjustedDueDate DESC
    """)
    List<TaxDeadline> findByClient(@Param("clientId") UUID clientId);

    /** Portfolio-level view — all clients in a date range. */
    @Query("""
        SELECT d FROM AccountantTaxDeadline d
        WHERE d.tenantId = :tenantId
          AND d.status IN ('PENDING','OVERDUE')
          AND d.adjustedDueDate BETWEEN :from AND :to
        ORDER BY d.adjustedDueDate ASC
    """)
    List<TaxDeadline> findInDateRange(@Param("tenantId") UUID tenantId,
                                      @Param("from") LocalDate from,
                                      @Param("to") LocalDate to);

    /**
     * Used by the daily scheduler to flip PENDING → OVERDUE.
     * Picks up any PENDING deadlines whose adjusted_due_date has passed.
     */
    @Query("""
        SELECT d FROM AccountantTaxDeadline d
        WHERE d.status = 'PENDING'
          AND d.adjustedDueDate < :today
    """)
    List<TaxDeadline> findOverdue(@Param("today") LocalDate today);

    /** D-30 reminders: deadlines due exactly 30 calendar days from now. */
    @Query("""
        SELECT d FROM AccountantTaxDeadline d
        WHERE d.status = 'PENDING'
          AND d.reminder30Sent = false
          AND d.adjustedDueDate = :targetDate
    """)
    List<TaxDeadline> findPendingReminder30(@Param("targetDate") LocalDate targetDate);

    /** D-7 reminders. */
    @Query("""
        SELECT d FROM AccountantTaxDeadline d
        WHERE d.status = 'PENDING'
          AND d.reminder7Sent = false
          AND d.adjustedDueDate = :targetDate
    """)
    List<TaxDeadline> findPendingReminder7(@Param("targetDate") LocalDate targetDate);

    /** D-1 reminders. */
    @Query("""
        SELECT d FROM AccountantTaxDeadline d
        WHERE d.status = 'PENDING'
          AND d.reminder1Sent = false
          AND d.adjustedDueDate = :targetDate
    """)
    List<TaxDeadline> findPendingReminder1(@Param("targetDate") LocalDate targetDate);
}
