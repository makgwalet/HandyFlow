package za.co.handyflow.platform.debtcollection.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.debtcollection.domain.model.PromiseToPay;
import za.co.handyflow.platform.shared.TenantId;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PromiseToPayRepository extends JpaRepository<PromiseToPay, UUID> {

    @Query("SELECT p FROM PromiseToPay p WHERE p.tenantId = :tenantId AND p.id = :id")
    Optional<PromiseToPay> findByTenantIdAndId(TenantId tenantId, UUID id);

    @Query("SELECT p FROM PromiseToPay p WHERE p.tenantId = :tenantId AND p.caseId = :caseId ORDER BY p.dueDate DESC, p.createdAt DESC")
    List<PromiseToPay> findByCaseId(TenantId tenantId, UUID caseId);

    /** At most one PROPOSED/ACTIVE promise per case at a time. */
    @Query("SELECT p FROM PromiseToPay p WHERE p.tenantId = :tenantId AND p.caseId = :caseId AND p.status IN ('PROPOSED','ACTIVE')")
    List<PromiseToPay> findOpenByCaseId(TenantId tenantId, UUID caseId);

    /** Cross-tenant daily sweep: every ACTIVE promise (early-kept detection + grace-expiry evaluation). */
    @Query("SELECT p FROM PromiseToPay p WHERE p.status = 'ACTIVE'")
    List<PromiseToPay> findAllActiveAcrossTenants();

    /** Cross-tenant: ACTIVE promises due within the reminder window. */
    @Query("SELECT p FROM PromiseToPay p WHERE p.status = 'ACTIVE' AND p.dueDate BETWEEN :from AND :to")
    List<PromiseToPay> findActiveDueWithinAcrossTenants(LocalDate from, LocalDate to);

    /** Metrics: promises resolved (kept/partial/broken) in a window. */
    @Query("""
        SELECT p FROM PromiseToPay p WHERE p.tenantId = :tenantId
        AND p.status IN ('KEPT','PARTIALLY_KEPT','BROKEN')
        AND p.resolvedDate BETWEEN :from AND :to
        """)
    List<PromiseToPay> findResolvedBetween(TenantId tenantId, LocalDate from, LocalDate to);
}
