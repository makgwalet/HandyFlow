package za.co.handyflow.platform.debtcollection.domain.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.debtcollection.domain.model.CaseStatus;
import za.co.handyflow.platform.debtcollection.domain.model.DebtCollectionCase;
import za.co.handyflow.platform.shared.TenantId;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DebtCollectionCaseRepository extends JpaRepository<DebtCollectionCase, UUID> {

    // FIX: same embedded-TenantId vs SpEL-unwrap type mismatch as
    // RegulatoryObligationRepository (see its comment for the full
    // explanation) — DebtCollectionCase also extends AggregateRoot, so
    // c.tenantId is an embedded TenantId, not a raw UUID column.
    @Query("""
        SELECT c FROM DebtCollectionCase c WHERE c.tenantId = :tenantId
        AND c.deletedAt IS NULL
        AND (:status IS NULL OR c.status = :status)
        ORDER BY c.openedDate DESC
        """)
    Page<DebtCollectionCase> findAllActive(TenantId tenantId, CaseStatus status, Pageable pageable);

    @Query("SELECT c FROM DebtCollectionCase c WHERE c.tenantId = :tenantId AND c.id = :id AND c.deletedAt IS NULL")
    Optional<DebtCollectionCase> findActiveById(TenantId tenantId, UUID id);

    /** Unpaginated — used by the PDF register export, same convention as legalcompliance's listAll(). */
    @Query("""
        SELECT c FROM DebtCollectionCase c WHERE c.tenantId = :tenantId AND c.deletedAt IS NULL
        ORDER BY c.openedDate DESC
        """)
    List<DebtCollectionCase> findAllActive(TenantId tenantId);

    @Query("SELECT COUNT(c) FROM DebtCollectionCase c WHERE c.tenantId = :tenantId AND c.deletedAt IS NULL")
    long countByTenant(TenantId tenantId);

    /** Used when opening a new case, to check for an existing open case against the same customer rather than fragmenting one debtor across several cases. */
    @Query("""
        SELECT c FROM DebtCollectionCase c WHERE c.tenantId = :tenantId
        AND c.customerId = :customerId AND c.deletedAt IS NULL AND c.status <> 'CLOSED'
        """)
    List<DebtCollectionCase> findOpenByCustomerId(TenantId tenantId, UUID customerId);

    /** Cross-tenant sweep for LegalComplianceNotificationScheduler-style proactive alerts on next-action-due dates. */
    @Query("""
        SELECT c FROM DebtCollectionCase c WHERE c.deletedAt IS NULL
        AND c.status <> 'CLOSED' AND c.nextActionDate BETWEEN :from AND :to
        """)
    List<DebtCollectionCase> findWithNextActionDueWithinAcrossTenants(LocalDate from, LocalDate to);
}