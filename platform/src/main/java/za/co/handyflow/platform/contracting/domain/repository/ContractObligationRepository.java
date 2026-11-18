package za.co.handyflow.platform.contracting.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.contracting.domain.model.ContractObligation;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ContractObligationRepository extends JpaRepository<ContractObligation, UUID> {

    Optional<ContractObligation> findByTenantIdAndId(UUID tenantId, UUID id);

    List<ContractObligation> findByTenantIdAndContractIdOrderByDueDateAsc(UUID tenantId, UUID contractId);

    /** Open obligations across the tenant's contracts, soonest first. A null {@code until} means no upper date. */
    @Query("SELECT o FROM ContractObligation o WHERE o.tenantId = :tenantId AND o.status IN ('OPEN','IN_PROGRESS') " +
            "AND (:until IS NULL OR o.dueDate <= :until) ORDER BY o.dueDate ASC")
    List<ContractObligation> findOpen(UUID tenantId, LocalDate until);

    @Query("SELECT count(o) FROM ContractObligation o WHERE o.tenantId = :tenantId AND o.status IN ('OPEN','IN_PROGRESS')")
    long countOpen(UUID tenantId);

    @Query("SELECT count(o) FROM ContractObligation o WHERE o.tenantId = :tenantId AND o.status IN ('OPEN','IN_PROGRESS') AND o.dueDate < :today")
    long countOverdue(UUID tenantId, LocalDate today);

    @Query("SELECT count(o) FROM ContractObligation o WHERE o.tenantId = :tenantId AND o.status IN ('OPEN','IN_PROGRESS') AND o.dueDate >= :today AND o.dueDate <= :until")
    long countDueBetween(UUID tenantId, LocalDate today, LocalDate until);

    /** For the reminder job: open obligations due within a week or already overdue (with or without a reminder address). */
    @Query("SELECT o FROM ContractObligation o WHERE o.status IN ('OPEN','IN_PROGRESS') AND o.dueDate <= :until")
    List<ContractObligation> findForReminders(LocalDate until);

    /** Obligations whose due date falls in the window, any status. */
    @Query("SELECT o FROM ContractObligation o WHERE o.tenantId = :tenantId AND o.dueDate >= :from AND o.dueDate <= :to")
    List<ContractObligation> findDueBetween(UUID tenantId, LocalDate from, LocalDate to);
}
